package com.jobtrack.service;

import com.jobtrack.config.MailProperties;
import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.entity.PasswordResetToken;
import com.jobtrack.entity.User;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.mail.EmailSender;
import com.jobtrack.mapper.UserMapper;
import com.jobtrack.repository.PasswordResetTokenRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Changing a password (logged in) and resetting a forgotten one (with an emailed one-time link).
 * Either way the old sessions on other devices stop working, and the caller gets a fresh token.
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    /** At most one email per account in this window, so nobody can flood someone's inbox. */
    static final Duration RESEND_WAIT = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                           PasswordEncoder passwordEncoder, JwtTokenService tokenService, EmailSender emailSender,
                           MailProperties mailProperties, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.clock = clock;
    }

    /** Logged in: needs the current password. Returns a new token, since older ones stop working. */
    @Transactional
    public AuthResponse changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("currentPassword", "That password isn't right.");
        }
        return setPassword(user, newPassword);
    }

    /**
     * Emails a reset link if an account has this address. Answers the same way either way, so the
     * form can't be used to find out who has an account.
     */
    @Transactional
    public void requestReset(String email) {
        if (!emailSender.enabled()) {
            throw new ConflictException("Resetting a password by email isn't available on this server yet.");
        }
        Optional<User> found = userRepository.findByEmail(AuthService.normalizeEmail(email));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        Instant now = clock.instant();
        if (tokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(RESEND_WAIT))) {
            return; // a link was just sent
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new PasswordResetToken(user.getId(), hash(token), now, now.plus(LINK_LIFETIME)));

        String link = mailProperties.appUrl() + "/reset-password?token=" + token;
        String text = """
                Hi %s,

                Someone asked to reset the password of your JobTrack account. To choose a new password, open this link (it works once, for 30 minutes):

                %s

                If it wasn't you, ignore this email: your password stays the same.
                """.formatted(user.getFullName(), link);
        String html = """
                <p>Hi %s,</p>
                <p>Someone asked to reset the password of your JobTrack account. To choose a new password, click the button below. The link works once, for 30 minutes.</p>
                <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#4f46e5;color:#fff;border-radius:8px;text-decoration:none">Choose a new password</a></p>
                <p>If it wasn't you, ignore this email: your password stays the same.</p>
                """.formatted(HtmlUtils.htmlEscape(user.getFullName()), HtmlUtils.htmlEscape(link));
        try {
            emailSender.send(user.getEmail(), "Reset your JobTrack password", text, html);
        } catch (EmailSender.EmailFailedException e) {
            // Logged for the operator; the user sees the same answer as always.
            log.warn("Could not send the password reset email for user {}: {}", user.getId(), e.getMessage());
        }
    }

    /** Uses a one-time link to set a new password, and logs the user in. */
    @Transactional
    public AuthResponse resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(token.strip()))
                .filter(t -> t.usableAt(now))
                .orElseThrow(() -> new BadRequestException("token",
                        "This reset link has expired or was already used. Ask for a new one."));
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BadRequestException("token", "This reset link is no longer valid."));
        resetToken.markUsed(now);
        return setPassword(user, newPassword);
    }

    private AuthResponse setPassword(User user, String newPassword) {
        // JWT issue times have whole-second precision, so the cut-off is whole seconds too: the
        // token issued right below (same second) stays valid, older ones don't.
        Instant changedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        user.changePassword(passwordEncoder.encode(newPassword), changedAt);
        userRepository.saveAndFlush(user);
        tokenRepository.deleteAllForUser(user.getId());
        JwtTokenService.IssuedToken issued = tokenService.issue(user);
        return AuthResponse.bearer(issued.value(), issued.expiresAt(), UserMapper.toResponse(user));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
