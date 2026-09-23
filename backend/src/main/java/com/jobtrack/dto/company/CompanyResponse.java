package com.jobtrack.dto.company;

import java.time.Instant;

public record CompanyResponse(
        Long id,
        String name,
        String website,
        String industry,
        String location,
        String notes,
        long applicationCount,
        Instant createdAt,
        Instant updatedAt) {
}
