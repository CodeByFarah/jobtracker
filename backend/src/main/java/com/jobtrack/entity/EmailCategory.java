package com.jobtrack.entity;

/** What the AI decided an email means for a job application. */
public enum EmailCategory {
    /** Confirms that an application was received. */
    APPLICATION_RECEIVED,
    /** Invites to, schedules or confirms an interview or call. */
    INTERVIEW,
    /** Asks the candidate to complete a test, assignment or online assessment. */
    ASSESSMENT,
    OFFER,
    REJECTED,
    /** About the application, but none of the above (e.g. a request for documents). */
    OTHER,
    /** Job alerts, newsletters, marketing, or anything not about a tracked application. */
    NOT_JOB_RELATED
}
