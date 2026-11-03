package com.jobtrack.security;

import com.jobtrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordChangeTokenValidatorTest {

    private static final Instant CHANGED = Instant.parse("2026-11-03T12:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordChangeTokenValidator validator = new PasswordChangeTokenValidator(users);

    private static Jwt token(Instant issuedAt) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject("7")
                .issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(3600)).build();
    }

    @Test
    void tokensFromBeforeThePasswordChangeAreRejected() {
        when(users.findPasswordChangedAt(7L)).thenReturn(Optional.of(CHANGED));

        assertThat(validator.validate(token(CHANGED.minusSeconds(1))).hasErrors()).isTrue();
        assertThat(validator.validate(token(CHANGED)).hasErrors()).isFalse();
        assertThat(validator.validate(token(CHANGED.plusSeconds(5))).hasErrors()).isFalse();
    }

    @Test
    void accountsThatNeverChangedTheirPasswordAreUnaffected() {
        when(users.findPasswordChangedAt(7L)).thenReturn(Optional.empty());

        assertThat(validator.validate(token(Instant.parse("2020-01-01T00:00:00Z"))).hasErrors()).isFalse();
    }
}
