package com.jobtrack.dto.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record EmailEventResponse(
        Long id,
        Long applicationId,
        String jobTitle,
        String companyName,
        String sender,
        String subject,
        Instant receivedAt,
        EmailCategory category,
        Double confidence,
        @Schema(description = "One-sentence summary written by the AI")
        String summary,
        ApplicationStatus statusBefore,
        @Schema(description = "Status the email moved the application to; null if it didn't change the status")
        ApplicationStatus statusAfter) {
}
