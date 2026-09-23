package com.jobtrack.dto.interview;

import com.jobtrack.entity.InterviewStatus;
import com.jobtrack.entity.InterviewType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body for creating (POST) and replacing (PUT) an interview; both accept the same fields.
 */
public record InterviewRequest(
        @NotNull
        InterviewType type,

        @NotNull
        @Schema(description = "ISO-8601 instant, e.g. 2026-10-01T14:00:00Z")
        Instant scheduledAt,

        @Positive @Max(1440)
        Integer durationMinutes,

        @Size(max = 150)
        String interviewerName,

        @Size(max = 255)
        String location,

        @Size(max = 10_000)
        String notes,

        @Schema(description = "Defaults to SCHEDULED")
        InterviewStatus status) {
}
