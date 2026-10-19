package com.jobtrack.dto.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/** Where one application stands, and the latest email the AI matched to it. */
public record ApplicationUpdate(
        Long id,
        String jobTitle,
        String companyName,
        ApplicationStatus status,
        LocalDate applicationDate,
        Instant updatedAt,
        Instant lastEmailAt,
        EmailCategory lastEmailCategory,
        String lastEmailSummary,
        @Schema(description = "The last check changed this application's status")
        boolean changedInLastCheck,
        @Schema(description = "Still Applied/Screening with no news for 14+ days")
        boolean noReply) {
}
