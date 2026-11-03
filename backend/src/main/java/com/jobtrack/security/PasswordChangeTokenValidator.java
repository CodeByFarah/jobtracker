package com.jobtrack.security;

import com.jobtrack.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

/**
 * Rejects tokens issued before the account's password last changed, so changing or resetting a
 * password signs out every other device (JWTs are otherwise valid until they expire).
 */
public class PasswordChangeTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SIGNED_OUT = new OAuth2Error("invalid_token",
            "The password changed after this token was issued", null);

    private final UserRepository userRepository;

    public PasswordChangeTokenValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(SIGNED_OUT);
        }
        Optional<Instant> changedAt = userRepository.findPasswordChangedAt(userId);
        Instant issuedAt = jwt.getIssuedAt();
        if (changedAt.isPresent() && (issuedAt == null || issuedAt.isBefore(changedAt.get()))) {
            return OAuth2TokenValidatorResult.failure(SIGNED_OUT);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
