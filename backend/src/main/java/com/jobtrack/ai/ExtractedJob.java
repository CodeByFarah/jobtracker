package com.jobtrack.ai;

import com.jobtrack.entity.EmploymentType;

/** Job details found on a posting page. Any field may be null when the page doesn't say. */
public record ExtractedJob(String jobTitle,
                           String companyName,
                           String location,
                           EmploymentType employmentType,
                           Integer salaryMin,
                           Integer salaryMax,
                           String salaryCurrency,
                           String summary) {

    /** Enough to create an application without asking the AI. */
    public boolean hasEssentials() {
        return jobTitle != null && companyName != null;
    }

    /** Fills this result's missing fields from {@code other}; values already present win. */
    public ExtractedJob orElse(ExtractedJob other) {
        if (other == null) {
            return this;
        }
        boolean ownSalary = salaryMin != null || salaryMax != null;
        return new ExtractedJob(
                jobTitle != null ? jobTitle : other.jobTitle,
                companyName != null ? companyName : other.companyName,
                location != null ? location : other.location,
                employmentType != null ? employmentType : other.employmentType,
                ownSalary ? salaryMin : other.salaryMin,
                ownSalary ? salaryMax : other.salaryMax,
                ownSalary ? salaryCurrency : other.salaryCurrency,
                summary != null ? summary : other.summary);
    }
}
