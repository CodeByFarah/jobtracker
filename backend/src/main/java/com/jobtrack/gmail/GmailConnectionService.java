package com.jobtrack.gmail;

import com.jobtrack.config.GmailProperties;
import com.jobtrack.entity.GmailConnection;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.GmailAuthException;
import com.jobtrack.repository.GmailConnectionRepository;
import com.jobtrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connecting and disconnecting Gmail, and handing out short-lived access tokens for the sync.
 * <p>
 * Each account has at most one mailbox, and each mailbox belongs to at most one account (both
 * enforced by unique constraints too). Connecting a different mailbox replaces the old one: its
 * access is revoked and the new mailbox is read from scratch.
 */
@Service
public class GmailConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GmailConnectionService.class);
    static final String ALREADY_CONNECTED_ELSEWHERE = "This Gmail account is already connected to another "
            + "JobTrack account. Disconnect it there first, or choose a different Gmail account.";

    private final GmailProperties properties;
    private final GmailApiClient gmail;
    private final OAuthStateSigner stateSigner;
    private final TokenCipher cipher;
    private final GmailConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    /** Access tokens live about an hour; caching them avoids a token refresh per sync. Keyed by connection id. */
    private final Map<Long, CachedToken> accessTokens = new ConcurrentHashMap<>();

    private record CachedToken(String value, Instant expiresAt) {
    }

    public GmailConnectionService(GmailProperties properties, GmailApiClient gmail, OAuthStateSigner stateSigner,
                                  TokenCipher cipher, GmailConnectionRepository connectionRepository,
                                  UserRepository userRepository, Clock clock) {
        this.properties = properties;
        this.gmail = gmail;
        this.stateSigner = stateSigner;
        this.cipher = cipher;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    /** The Google sign-in URL, and the nonce the browser must bring back to the callback. */
    public ConnectStart connectUrl(Long userId) {
        if (!properties.configured()) {
            log.warn("Gmail connect requested but GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET are not set");
            throw new ConflictException("Connecting Gmail isn't available right now. Please try again later.");
        }
        OAuthStateSigner.State state = stateSigner.create(userId);
        return new ConnectStart(gmail.authorizationUrl(state.value()), state.browserNonce());
    }

    public record ConnectStart(String url, String browserNonce) {
    }

    /**
     * Finishes the Google sign-in: checks the state (and that this browser started it), swaps the
     * code for tokens and stores the encrypted refresh token. Returns the user id the mailbox was
     * connected to.
     */
    @Transactional
    public long completeConnection(String code, String state, String browserNonce) {
        long userId = stateSigner.verify(state, browserNonce);
        GmailApiClient.Tokens tokens = gmail.exchangeCode(code);
        if (tokens.refreshToken() == null) {
            throw new GmailAuthException("Google didn't grant offline access. Remove JobTrack under "
                    + "\"Third-party apps\" in your Google account and connect again.");
        }
        String address = gmail.emailAddress(tokens.accessToken());

        Optional<GmailConnection> owner = connectionRepository.findByGmailAddressIgnoreCase(address);
        if (owner.isPresent() && !owner.get().getUserId().equals(userId)) {
            // Don't revoke: that would cut off the account that legitimately has this mailbox.
            throw new GmailAuthException(ALREADY_CONNECTED_ELSEWHERE);
        }

        GmailConnection connection = connectionRepository.findByUserId(userId).orElse(null);
        if (connection != null && !connection.getGmailAddress().equalsIgnoreCase(address)) {
            // Switching mailboxes: the old one stops being tracked entirely. A new row (new id) also
            // makes any check still running on the old mailbox stop instead of writing to the new one.
            revokeQuietly(connection);
            accessTokens.remove(connection.getId());
            connectionRepository.delete(connection);
            connectionRepository.flush();
            connection = null;
        }
        if (connection == null) {
            connection = new GmailConnection(userRepository.getReferenceById(userId));
        }
        connection.setGmailAddress(address);
        connection.setRefreshTokenEncrypted(cipher.encrypt(tokens.refreshToken()));
        connection.setLastSyncError(null);
        try {
            connection = connectionRepository.saveAndFlush(connection);
        } catch (DataIntegrityViolationException e) {
            // Another account connected the same mailbox at the same moment.
            throw new GmailAuthException(ALREADY_CONNECTED_ELSEWHERE);
        }
        accessTokens.put(connection.getId(), new CachedToken(tokens.accessToken(), expiry(tokens)));
        return userId;
    }

    @Transactional(readOnly = true)
    public Optional<GmailConnection> find(Long userId) {
        return connectionRepository.findByUserId(userId);
    }

    /** A valid access token, refreshing it with the stored refresh token when needed. */
    public String accessToken(GmailConnection connection) {
        CachedToken cached = accessTokens.get(connection.getId());
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value();
        }
        String refreshToken;
        try {
            refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted());
        } catch (IllegalStateException e) {
            throw new GmailAuthException("The saved Gmail access can't be read any more. Please reconnect Gmail.", e);
        }
        GmailApiClient.Tokens tokens = gmail.refresh(refreshToken);
        accessTokens.put(connection.getId(), new CachedToken(tokens.accessToken(), expiry(tokens)));
        return tokens.accessToken();
    }

    /** Revokes access at Google and forgets the mailbox. Jobs already tracked stay. */
    @Transactional
    public void disconnect(Long userId) {
        connectionRepository.findByUserId(userId).ifPresent(connection -> {
            revokeQuietly(connection);
            accessTokens.remove(connection.getId());
            connectionRepository.delete(connection);
        });
    }

    private void revokeQuietly(GmailConnection connection) {
        try {
            gmail.revoke(cipher.decrypt(connection.getRefreshTokenEncrypted()));
        } catch (RestClientException | IllegalStateException e) {
            // Already revoked or unreadable: deleting our copy is what matters.
            log.info("Could not revoke Gmail token for user {}: {}", connection.getUserId(), e.getMessage());
        }
    }

    private Instant expiry(GmailApiClient.Tokens tokens) {
        // Refresh a minute early so a token never expires halfway through a request.
        return clock.instant().plusSeconds(Math.max(60, tokens.expiresInSeconds() - 60));
    }
}
