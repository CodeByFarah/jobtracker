package com.jobtrack.dto.application;

/**
 * Whitelist of sortable columns for the application list, so clients cannot sort by arbitrary
 * (or non-existent) entity properties.
 */
public enum ApplicationSortField {
    APPLICATION_DATE("applicationDate"),
    UPDATED_AT("updatedAt"),
    CREATED_AT("createdAt"),
    JOB_TITLE("jobTitle");

    private final String property;

    ApplicationSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
