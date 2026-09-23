package com.jobtrack.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Stages of the hiring pipeline, together with the transitions that are allowed between them.
 *
 * <pre>
 * SAVED -> APPLIED -> SCREENING -> INTERVIEW -> OFFER -> ACCEPTED
 * </pre>
 * Stages may be skipped going forward (e.g. APPLIED -> INTERVIEW), REJECTED and WITHDRAWN can be
 * reached from any open stage, and ACCEPTED / REJECTED / WITHDRAWN are final.
 */
public enum ApplicationStatus {
    SAVED,
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    /** Statuses that represent an application still moving through a hiring process. */
    public static final Set<ApplicationStatus> ACTIVE = EnumSet.of(APPLIED, SCREENING, INTERVIEW, OFFER);

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            SAVED, EnumSet.of(APPLIED, WITHDRAWN),
            APPLIED, EnumSet.of(SCREENING, INTERVIEW, OFFER, REJECTED, WITHDRAWN),
            SCREENING, EnumSet.of(INTERVIEW, OFFER, REJECTED, WITHDRAWN),
            INTERVIEW, EnumSet.of(OFFER, REJECTED, WITHDRAWN),
            OFFER, EnumSet.of(ACCEPTED, REJECTED, WITHDRAWN),
            ACCEPTED, EnumSet.noneOf(ApplicationStatus.class),
            REJECTED, EnumSet.noneOf(ApplicationStatus.class),
            WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));

    public boolean canTransitionTo(ApplicationStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<ApplicationStatus> allowedTransitions() {
        // Values are EnumSets, so copyOf is safe even when the set is empty.
        return EnumSet.copyOf(ALLOWED_TRANSITIONS.get(this));
    }

    public boolean isClosed() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
