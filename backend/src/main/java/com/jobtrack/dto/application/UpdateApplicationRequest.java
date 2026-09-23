package com.jobtrack.dto.application;

import com.jobtrack.entity.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

/**
 * Replaces the editable details of an application. Status is deliberately absent: it can only be
 * changed through {@code PATCH /api/applications/{id}/status} so every change is recorded in the
 * status history.
 */
public record UpdateApplicationRequest(
        @NotNull
        Long companyId,

        @NotBlank @Size(max = 150)
        String jobTitle,

        @Size(max = 500) @URL(regexp = "^https?:.*", message = "must be a valid http(s) URL")
        String jobUrl,

        @Size(max = 150)
        String location,

        EmploymentType employmentType,

        @PositiveOrZero
        Integer salaryMin,

        @PositiveOrZero
        Integer salaryMax,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO currency code")
        String salaryCurrency,

        LocalDate applicationDate,

        @Size(max = 10_000)
        String notes) implements ApplicationFields {
}
