package com.jobtrack.dto.ai;

import com.jobtrack.entity.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Job details read from a posting link. Nothing is saved: the client shows these values in the
 * "new application" form so the user can check them before creating the application.
 */
public record JobLinkPreview(
        String jobUrl,
        String jobTitle,
        String companyName,
        @Schema(description = "Id of the user's existing company with the same name, if any")
        Long matchedCompanyId,
        String location,
        EmploymentType employmentType,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String notes,
        @Schema(description = "PAGE_DATA (read from the page's structured data), AI, or PAGE_DATA_AND_AI")
        String source) {
}
