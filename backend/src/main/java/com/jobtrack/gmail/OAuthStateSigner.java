package com.jobtrack.gmail;

import com.jobtrack.config.JwtProperties;
import com.jobtrack.exception.BadRequestException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * The OAuth {@code state} parameter. Google sends it back to the (unauthenticated) callback, so it
 * carries the user id, an expiry and a random nonce, signed with HMAC-SHA256. A forged or expired
 * state is rejected.
 * <p>
 * The nonce is also given to the browser that started the sign-in (as a cookie), and the callback
 * only accepts the state together with that cookie. Otherwise someone could start a sign-in on their
 * own account, send the Google link to another person, and have that person's mailbox connected
 * to the attacker's account.
 */
@Component
public class OAuthStateSigner {

    static final Duration VALIDITY = Duration.ofMinutes(15);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** The signed state for Google, and the nonce the starting browser must present at the callback. */
    public record State(String value, String browserNonce) {
    }

    public OAuthStateSigner(JwtProperties jwtProperties, Clock clock) {
        this.key = TokenCipher.sha256("jobtrack-gmail-oauth-state:" + jwtProperties.secret());
        this.clock = clock;
    }

    public State create(long userId) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String browserNonce = ENCODER.encodeToString(nonce);
        long expiresAt = clock.instant().plus(VALIDITY).getEpochSecond();
        String payload = userId + "." + expiresAt + "." + browserNonce;
        String value = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + ENCODER.encodeToString(sign(payload));
        return new State(value, browserNonce);
    }

    /** Returns the user id the state was created for, if it is genuine, unexpired and from this browser. */
    public long verify(String state, String browserNonce) {
        try {
            String[] parts = state.split("\\.");
            if (parts.length != 2) {
                throw invalid();
            }
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload), DECODER.decode(parts[1]))) {
                throw invalid();
            }
            String[] fields = payload.split("\\.");
            if (fields.length != 3 || Long.parseLong(fields[1]) < clock.instant().getEpochSecond()) {
                throw invalid();
            }
            if (browserNonce == null || !MessageDigest.isEqual(
                    fields[2].getBytes(StandardCharsets.UTF_8), browserNonce.getBytes(StandardCharsets.UTF_8))) {
                throw new BadRequestException(null, "Please start connecting Gmail from JobTrack in this browser.");
            }
            return Long.parseLong(fields[0]);
        } catch (IllegalArgumentException e) { // bad Base64 or number
            throw invalid();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException(null, "The Gmail sign-in link expired or is invalid. Please try connecting again.");
    }
}
