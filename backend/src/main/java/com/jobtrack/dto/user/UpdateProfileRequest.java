package com.jobtrack.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email is the login identity and is intentionally not editable here. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 150) String headline,
        @Size(max = 150) String location) {
}
