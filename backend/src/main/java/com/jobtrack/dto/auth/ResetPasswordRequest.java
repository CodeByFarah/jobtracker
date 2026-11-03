package com.jobtrack.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 200)
        String token,

        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String newPassword) {
}
