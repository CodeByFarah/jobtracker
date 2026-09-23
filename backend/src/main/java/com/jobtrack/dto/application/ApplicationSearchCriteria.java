package com.jobtrack.dto.application;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmploymentType;

/**
 * Optional filters for the application list; null fields are ignored.
 *
 * @param query          matched (case-insensitive, substring) against job title and company name
 * @param status         exact status
 * @param location       case-insensitive substring of the job location
 * @param employmentType exact employment type
 * @param companyId      only applications at this company
 */
public record ApplicationSearchCriteria(
        String query,
        ApplicationStatus status,
        String location,
        EmploymentType employmentType,
        Long companyId) {
}
