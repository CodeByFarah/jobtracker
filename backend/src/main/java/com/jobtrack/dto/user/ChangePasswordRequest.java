package com.jobtrack.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        // Same rule as registration: BCrypt only uses the first 72 bytes.
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String newPassword) {
}
