package com.jobtrack.entity;

public enum InterviewStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    RESCHEDULED;

    /** An interview that is still expected to happen. */
    public boolean isPending() {
        return this == SCHEDULED || this == RESCHEDULED;
    }
}
