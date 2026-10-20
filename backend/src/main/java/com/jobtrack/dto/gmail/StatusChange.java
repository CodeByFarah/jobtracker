package com.jobtrack.dto.gmail;

import com.jobtrack.entity.ApplicationStatus;

/**
 * One application whose status an email changed during a check. {@code from} is null when the
 * application was added from the email (the user hadn't tracked it yet).
 */
public record StatusChange(Long applicationId, String jobTitle, String companyName,
                           ApplicationStatus from, ApplicationStatus to, String emailSummary) {
}
