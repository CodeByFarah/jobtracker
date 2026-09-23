package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * JWT settings, bound from {@code jobtrack.jwt.*} (populated from the JWT_SECRET and
 * JWT_EXPIRATION_MINUTES environment variables). Startup fails fast on a missing or weak secret.
 */
@ConfigurationProperties(prefix = "jobtrack.jwt")
public record JwtProperties(String secret, long expirationMinutes, String issuer) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long (see .env.example)");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be positive");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "jobtrack";
        }
    }
}
