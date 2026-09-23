package com.jobtrack.dto.application;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record ApplicationResponse(
        Long id,
        String jobTitle,
        CompanySummary company,
        String jobUrl,
        String location,
        EmploymentType employmentType,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        LocalDate applicationDate,
        ApplicationStatus status,
        @Schema(description = "Statuses this application can move to next")
        Set<ApplicationStatus> allowedTransitions,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public record CompanySummary(Long id, String name) {
    }
}
