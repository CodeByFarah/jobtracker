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
 * state is rejected, which stops another site from attaching its own Gmail to someone's account.
 */
@Component
public class OAuthStateSigner {

    static final Duration VALIDITY = Duration.ofMinutes(15);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OAuthStateSigner(JwtProperties jwtProperties, Clock clock) {
        this.key = TokenCipher.sha256("jobtrack-gmail-oauth-state:" + jwtProperties.secret());
        this.clock = clock;
    }

    public String create(long userId) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        long expiresAt = clock.instant().plus(VALIDITY).getEpochSecond();
        String payload = userId + "." + expiresAt + "." + ENCODER.encodeToString(nonce);
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + ENCODER.encodeToString(sign(payload));
    }

    /** Returns the user id the state was created for. */
    public long verify(String state) {
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
