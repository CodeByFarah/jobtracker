package com.jobtrack.dto.user;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String headline,
        String location,
        Instant createdAt) {
}
