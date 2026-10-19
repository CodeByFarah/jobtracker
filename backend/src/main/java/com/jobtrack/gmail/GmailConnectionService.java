package com.jobtrack.gmail;

import com.jobtrack.config.GmailProperties;
import com.jobtrack.entity.GmailConnection;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.GmailAuthException;
import com.jobtrack.repository.GmailConnectionRepository;
import com.jobtrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Connecting and disconnecting Gmail, and handing out short-lived access tokens for the sync. */
@Service
public class GmailConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GmailConnectionService.class);

    private final GmailProperties properties;
    private final GmailApiClient gmail;
    private final OAuthStateSigner stateSigner;
    private final TokenCipher cipher;
    private final GmailConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    /** Access tokens live about an hour; caching them avoids a token refresh per sync. */
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

    public String connectUrl(Long userId) {
        if (!properties.configured()) {
            throw new ConflictException("Gmail isn't set up on this server yet: add GOOGLE_CLIENT_ID and "
                    + "GOOGLE_CLIENT_SECRET to the .env file and restart the backend.");
        }
        return gmail.authorizationUrl(stateSigner.create(userId));
    }

    /**
     * Finishes the Google sign-in: checks the state, swaps the code for tokens and stores the
     * encrypted refresh token. Returns the user id the mailbox was connected to.
     */
    @Transactional
    public long completeConnection(String code, String state) {
        long userId = stateSigner.verify(state);
        GmailApiClient.Tokens tokens = gmail.exchangeCode(code);
        if (tokens.refreshToken() == null) {
            throw new GmailAuthException("Google didn't grant offline access. Remove JobTrack under "
                    + "\"Third-party apps\" in your Google account and connect again.");
        }
        String address = gmail.emailAddress(tokens.accessToken());

        GmailConnection connection = connectionRepository.findByUserId(userId)
                .orElseGet(() -> new GmailConnection(userRepository.getReferenceById(userId)));
        if (connection.getGmailAddress() != null && !connection.getGmailAddress().equalsIgnoreCase(address)) {
            connection.setLastSyncedAt(null); // a different mailbox: start its history from scratch
        }
        connection.setGmailAddress(address);
        connection.setRefreshTokenEncrypted(cipher.encrypt(tokens.refreshToken()));
        connection.setLastSyncError(null);
        connectionRepository.save(connection);
        accessTokens.put(userId, new CachedToken(tokens.accessToken(), expiry(tokens)));
        return userId;
    }

    @Transactional(readOnly = true)
    public Optional<GmailConnection> find(Long userId) {
        return connectionRepository.findByUserId(userId);
    }

    /** A valid access token, refreshing it with the stored refresh token when needed. */
    public String accessToken(GmailConnection connection) {
        Long userId = connection.getUserId();
        CachedToken cached = accessTokens.get(userId);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.value();
        }
        String refreshToken;
        try {
            refreshToken = cipher.decrypt(connection.getRefreshTokenEncrypted());
        } catch (IllegalStateException e) {
            throw new GmailAuthException("The saved Gmail access can't be read any more (was JWT_SECRET changed?). "
                    + "Please reconnect Gmail.", e);
        }
        GmailApiClient.Tokens tokens = gmail.refresh(refreshToken);
        accessTokens.put(userId, new CachedToken(tokens.accessToken(), expiry(tokens)));
        return tokens.accessToken();
    }

    @Transactional
    public void disconnect(Long userId) {
        connectionRepository.findByUserId(userId).ifPresent(connection -> {
            try {
                gmail.revoke(cipher.decrypt(connection.getRefreshTokenEncrypted()));
            } catch (RestClientException | IllegalStateException e) {
                // Already revoked or unreadable: deleting our copy is what matters.
                log.info("Could not revoke Gmail token for user {}: {}", userId, e.getMessage());
            }
            connectionRepository.delete(connection);
        });
        accessTokens.remove(userId);
    }

    private Instant expiry(GmailApiClient.Tokens tokens) {
        // Refresh a minute early so a token never expires halfway through a request.
        return clock.instant().plusSeconds(Math.max(60, tokens.expiresInSeconds() - 60));
    }
}
