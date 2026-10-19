package com.jobtrack.gmail;

import com.jobtrack.entity.EmailCategory;

/**
 * The AI's reading of one email.
 *
 * @param applicationId the matching application, or null if the email isn't about any of them
 * @param confidence    0..1, how sure the model says it is
 */
public record EmailClassification(Long applicationId, EmailCategory category, double confidence, String summary) {
}
