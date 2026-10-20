package com.jobtrack.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CompanyResponse(
        Long id,
        String name,
        String website,
        String industry,
        String location,
        String notes,
        long applicationCount,
        @Schema(description = "When the logo last changed; null when there is none (see GET /api/companies/{id}/logo)")
        Instant logoUpdatedAt,
        Instant createdAt,
        Instant updatedAt) {
}
