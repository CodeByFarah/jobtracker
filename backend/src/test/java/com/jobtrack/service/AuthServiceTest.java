package com.jobtrack.service;

import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.dto.auth.LoginRequest;
import com.jobtrack.dto.auth.RegisterRequest;
import com.jobtrack.entity.User;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.JwtTokenService;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Low cost factor keeps the test fast; production uses the default (10).
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    UserRepository userRepository;
    @Mock
    JwtTokenService tokenService;

    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void registerStoresNormalisedEmailAndHashedPassword() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), 1L));
        when(tokenService.issue(any())).thenReturn(new JwtTokenService.IssuedToken("token", Instant.EPOCH));

        AuthResponse response = service.register(new RegisterRequest("  Ada@Example.COM ", "password123", " Ada "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ada");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123").startsWith("$2");
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(response.accessToken()).isEqualTo("token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void registerRejectsExistingEmail() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("ADA@example.com", "password123", "Ada")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithCorrectPasswordIssuesToken() {
        User user = TestEntities.withId(new User("ada@example.com", passwordEncoder.encode("password123"), "Ada"), 1L);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(new JwtTokenService.IssuedToken("token", Instant.EPOCH));

        assertThat(service.login(new LoginRequest("Ada@example.com", "password123")).accessToken()).isEqualTo("token");
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailFailsTheSameWay() {
        User user = TestEntities.withId(new User("ada@example.com", passwordEncoder.encode("password123"), "Ada"), 1L);
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ada@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(tokenService, never()).issue(any());
    }
}
