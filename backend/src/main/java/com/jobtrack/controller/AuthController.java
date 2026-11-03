package com.jobtrack.controller;

import com.jobtrack.dto.auth.AuthResponse;
import com.jobtrack.dto.auth.ForgotPasswordRequest;
import com.jobtrack.dto.auth.LoginRequest;
import com.jobtrack.dto.auth.RegisterRequest;
import com.jobtrack.dto.auth.ResetPasswordRequest;
import com.jobtrack.service.AuthService;
import com.jobtrack.service.PasswordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register, log in and reset a forgotten password. These endpoints are public.")
@SecurityRequirements // public: no bearer token required
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;

    public AuthController(AuthService authService, PasswordService passwordService) {
        this.authService = authService;
        this.passwordService = passwordService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account", description = "Returns an access token so the user is logged in immediately.")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = """
            Exchanges email and password for a JWT access token. Send it as `Authorization: Bearer <token>`. \
            Logging out is client-side: the client discards the token, which expires on its own.""")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Email a password reset link", description = """
            Sends a one-time link (valid 30 minutes) if an account has this email. The answer is the \
            same whether or not the account exists.""")
    @ApiResponse(responseCode = "204", description = "Handled (an email is sent if the account exists)")
    @ApiResponse(responseCode = "409", description = "Email isn't set up on this server")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password with a reset link",
            description = "Signs out other devices and returns an access token, so the user is logged in.")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Link expired, already used, or the password is too short")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return passwordService.resetPassword(request.token(), request.newPassword());
    }
}
