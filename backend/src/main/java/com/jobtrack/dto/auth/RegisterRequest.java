package com.jobtrack.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password,

        @NotBlank @Size(max = 100)
        String fullName) {
}
