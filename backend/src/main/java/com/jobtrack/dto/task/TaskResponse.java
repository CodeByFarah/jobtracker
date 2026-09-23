package com.jobtrack.dto.task;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        Long applicationId,
        String jobTitle,
        String companyName,
        String title,
        String description,
        LocalDate dueDate,
        boolean completed,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {
}
