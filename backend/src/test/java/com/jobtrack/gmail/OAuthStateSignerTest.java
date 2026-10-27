package com.jobtrack.gmail;

import com.jobtrack.config.JwtProperties;
import com.jobtrack.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthStateSignerTest {

    private static final JwtProperties JWT = new JwtProperties("test-secret-key-that-is-at-least-32-bytes-long", 60, "jobtrack");
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void stateRoundTripsToTheUserId() {
        OAuthStateSigner signer = new OAuthStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(signer.verify(signer.create(42))).isEqualTo(42);
    }

    @Test
    void tamperedStateIsRejected() {
        OAuthStateSigner signer = new OAuthStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC));
        String state = signer.create(42);
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("7." + (NOW.getEpochSecond() + 600) + ".abc").getBytes()) + state.substring(state.indexOf('.'));

        assertThatThrownBy(() -> signer.verify(forged)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> signer.verify("garbage")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredStateIsRejected() {
        String state = new OAuthStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC)).create(42);
        OAuthStateSigner later = new OAuthStateSigner(JWT,
                Clock.fixed(NOW.plus(OAuthStateSigner.VALIDITY).plusSeconds(5), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.verify(state)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void tokenCipherRoundTripsAndUsesAFreshIvEachTime() {
        TokenCipher cipher = new TokenCipher(JWT);
        String first = cipher.encrypt("1//refresh-token");
        String second = cipher.encrypt("1//refresh-token");

        assertThat(first).isNotEqualTo(second).doesNotContain("refresh-token");
        assertThat(cipher.decrypt(first)).isEqualTo("1//refresh-token");
        assertThat(cipher.decrypt(second)).isEqualTo("1//refresh-token");
    }
}
