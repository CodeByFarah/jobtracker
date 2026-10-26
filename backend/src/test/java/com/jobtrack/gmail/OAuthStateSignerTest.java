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
        OAuthStateSigner.State state = signer.create(42);
        assertThat(signer.verify(state.value(), state.browserNonce())).isEqualTo(42);
    }

    @Test
    void stateIsOnlyAcceptedFromTheBrowserThatStartedTheSignIn() {
        OAuthStateSigner signer = new OAuthStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC));
        OAuthStateSigner.State attackers = signer.create(42);
        OAuthStateSigner.State victims = signer.create(7);

        // The attacker's Google link opened in someone else's browser (no or another cookie).
        assertThatThrownBy(() -> signer.verify(attackers.value(), null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> signer.verify(attackers.value(), victims.browserNonce()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void tamperedStateIsRejected() {
        OAuthStateSigner signer = new OAuthStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC));
        String state = signer.create(42).value();
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("7." + (NOW.getEpochSecond() + 600) + ".abc").getBytes()) + state.substring(state.indexOf('.'));

        assertThatThrownBy(() -> signer.verify(forged, "abc")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> signer.verify("garbage", "abc")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredStateIsRejected() {
        OAuthStateSigner.State state = new OAuthStateSigner(JWT, Clock.fixed(NOW, ZoneOffset.UTC)).create(42);
        OAuthStateSigner later = new OAuthStateSigner(JWT,
                Clock.fixed(NOW.plus(OAuthStateSigner.VALIDITY).plusSeconds(5), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.verify(state.value(), state.browserNonce())).isInstanceOf(BadRequestException.class);
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
