package com.jobtrack.gmail;

import com.jobtrack.entity.EmailCategory;

/**
 * The AI's reading of one email.
 *
 * @param applicationId the matching tracked application, or null if the email isn't about any of them
 * @param confidence    0..1, how sure the model says it is
 * @param companyName   the hiring company the email is about, as written in the email (may be null)
 * @param jobTitle      the job the email is about, if it says (may be null)
 */
public record EmailClassification(Long applicationId, EmailCategory category, double confidence, String summary,
                                  String companyName, String jobTitle) {

    public EmailClassification(Long applicationId, EmailCategory category, double confidence, String summary) {
        this(applicationId, category, confidence, summary, null, null);
    }
}
