package com.jobtrack.service;

import com.jobtrack.config.MailProperties;
import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.entity.PasswordResetToken;
import com.jobtrack.entity.User;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.mail.EmailSender;
import com.jobtrack.repository.PasswordResetTokenRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.JwtTokenService;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00.750Z");

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final MailProperties mail = new MailProperties("log", null, null, null, "https://example.com/app/");

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordResetTokenRepository tokenRepository;
    @Mock
    JwtTokenService tokenService;
    @Mock
    EmailSender emailSender;

    PasswordService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new PasswordService(userRepository, tokenRepository, passwordEncoder, tokenService, emailSender,
                mail, Clock.fixed(NOW, ZoneOffset.UTC));
        user = TestEntities.user(7);
        user.changePassword(passwordEncoder.encode("old-password"), null);
    }

    private void tokenIssued() {
        when(tokenService.issue(any())).thenReturn(new JwtTokenService.IssuedToken("new-token", NOW));
    }

    @Test
    void changePasswordNeedsTheCurrentOne() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(7L, "wrong", "new-password"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("isn't right");
        assertThat(passwordEncoder.matches("old-password", user.getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordStoresTheNewHashSignsOutOldTokensAndReturnsAFreshOne() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        tokenIssued();

        AuthResponse response = service.changePassword(7L, "old-password", "new-password");

        assertThat(passwordEncoder.matches("new-password", user.getPasswordHash())).isTrue();
        // Whole seconds, so the token issued in the same second (JWT iat precision) stays valid.
        assertThat(user.getPasswordChangedAt()).isEqualTo(Instant.parse("2026-11-03T12:00:00Z"));
        assertThat(response.accessToken()).isEqualTo("new-token");
        verify(tokenRepository).deleteAllForUser(7L);
    }

    @Test
    void forgotPasswordSaysSoWhenEmailIsntSetUp() {
        when(emailSender.enabled()).thenReturn(false);

        assertThatThrownBy(() -> service.requestReset("user7@example.com")).isInstanceOf(ConflictException.class);
    }

    @Test
    void forgotPasswordForAnUnknownEmailDoesNothingVisible() {
        when(emailSender.enabled()).thenReturn(true);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset(" Nobody@Example.com ");

        verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyString());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void forgotPasswordStoresOnlyAHashAndEmailsTheLink() {
        when(emailSender.enabled()).thenReturn(true);
        when(userRepository.findByEmail("user7@example.com")).thenReturn(Optional.of(user));

        service.requestReset("user7@example.com");

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("user7@example.com"), anyString(), text.capture(), anyString());

        String link = text.getValue().lines().filter(l -> l.startsWith("https://")).findFirst().orElseThrow();
        assertThat(link).startsWith("https://example.com/app/reset-password?token=");
        String token = link.substring(link.indexOf("token=") + 6);
        assertThat(token).hasSizeGreaterThan(30);
        // The database never sees the token itself.
        assertThat(saved.getValue()).extracting("tokenHash").isEqualTo(PasswordService.hash(token));
        assertThat(saved.getValue().usableAt(NOW.plus(PasswordService.LINK_LIFETIME).minusSeconds(1))).isTrue();
        assertThat(saved.getValue().usableAt(NOW.plus(PasswordService.LINK_LIFETIME))).isFalse();
    }

    @Test
    void forgotPasswordDoesntSendAgainWithinAMinute() {
        when(emailSender.enabled()).thenReturn(true);
        when(userRepository.findByEmail("user7@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.existsByUserIdAndCreatedAtAfter(eq(7L), any())).thenReturn(true);

        service.requestReset("user7@example.com");

        verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resetWithAValidLinkSetsThePasswordAndUsesTheLinkUp() {
        PasswordResetToken token = new PasswordResetToken(7L, PasswordService.hash("abc"), NOW, NOW.plusSeconds(600));
        when(tokenRepository.findByTokenHash(PasswordService.hash("abc"))).thenReturn(Optional.of(token));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        tokenIssued();

        AuthResponse response = service.resetPassword("abc", "brand-new-password");

        assertThat(passwordEncoder.matches("brand-new-password", user.getPasswordHash())).isTrue();
        assertThat(token.usableAt(NOW)).isFalse();
        assertThat(response.accessToken()).isEqualTo("new-token");
        verify(tokenRepository).deleteAllForUser(7L);
    }

    @Test
    void expiredOrUsedLinksAreRefused() {
        PasswordResetToken expired = new PasswordResetToken(7L, PasswordService.hash("old"), NOW.minusSeconds(3600),
                NOW.minusSeconds(1));
        when(tokenRepository.findByTokenHash(PasswordService.hash("old"))).thenReturn(Optional.of(expired));
        when(tokenRepository.findByTokenHash(PasswordService.hash("unknown"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("old", "brand-new-password"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> service.resetPassword("unknown", "brand-new-password"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }
}
