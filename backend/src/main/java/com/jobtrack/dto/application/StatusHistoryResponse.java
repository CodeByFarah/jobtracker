package com.jobtrack.dto.application;

import com.jobtrack.entity.ApplicationStatus;

import java.time.Instant;

public record StatusHistoryResponse(
        Long id,
        Long applicationId,
        ApplicationStatus previousStatus,
        ApplicationStatus newStatus,
        Instant changedAt) {
}
