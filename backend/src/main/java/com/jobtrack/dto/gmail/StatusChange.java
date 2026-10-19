package com.jobtrack.dto.gmail;

import com.jobtrack.entity.ApplicationStatus;

/** One application whose status an email changed during a check. */
public record StatusChange(Long applicationId, String jobTitle, String companyName,
                           ApplicationStatus from, ApplicationStatus to, String emailSummary) {
}
