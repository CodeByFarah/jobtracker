package com.jobtrack.dto.application;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record CreateApplicationRequest(
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

        @Schema(description = "Date the application was submitted. Defaults to today when the status is past SAVED.")
        LocalDate applicationDate,

        @Schema(description = "Initial status. Defaults to SAVED.")
        ApplicationStatus status,

        @Size(max = 10_000)
        String notes) implements ApplicationFields {
}
