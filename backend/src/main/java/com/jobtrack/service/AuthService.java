package com.jobtrack.service;

import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.dto.auth.LoginRequest;
import com.jobtrack.dto.auth.RegisterRequest;
import com.jobtrack.entity.User;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.mapper.UserMapper;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    /** Compared against when the email is unknown, so both failure paths take similar time. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode("jobtrack-timing-equaliser");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), TextUtils.clean(request.fullName()));
        userRepository.save(user);
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            // Same message for unknown email and wrong password: don't reveal which accounts exist.
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueToken(user.get());
    }

    private AuthResponse issueToken(User user) {
        JwtTokenService.IssuedToken token = tokenService.issue(user);
        return AuthResponse.bearer(token.value(), token.expiresAt(), UserMapper.toResponse(user));
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
