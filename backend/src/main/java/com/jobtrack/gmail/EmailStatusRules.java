package com.jobtrack.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;

import java.util.List;

/**
 * How an email's meaning moves an application forward. Emails can only move an application along
 * the normal status workflow (see {@link ApplicationStatus}), never backwards: an old "we received
 * your application" email can't undo an interview.
 */
public final class EmailStatusRules {

    private EmailStatusRules() {
    }

    /** The status an email of this kind points to, or null if it doesn't imply one. */
    public static ApplicationStatus targetStatus(EmailCategory category) {
        return switch (category) {
            case APPLICATION_RECEIVED -> ApplicationStatus.APPLIED;
            case ASSESSMENT -> ApplicationStatus.SCREENING;
            case INTERVIEW -> ApplicationStatus.INTERVIEW;
            case OFFER -> ApplicationStatus.OFFER;
            case REJECTED -> ApplicationStatus.REJECTED;
            case OTHER, NOT_JOB_RELATED -> null;
        };
    }

    /**
     * The status changes to apply, in order. Usually one step; an application still marked SAVED
     * first moves to APPLIED (the email proves the user applied), so the history stays valid.
     * Empty when the email doesn't move the application forward.
     */
    public static List<ApplicationStatus> steps(ApplicationStatus current, EmailCategory category) {
        ApplicationStatus target = targetStatus(category);
        if (target == null || target == current) {
            return List.of();
        }
        if (current.canTransitionTo(target)) {
            return List.of(target);
        }
        if (current == ApplicationStatus.SAVED && ApplicationStatus.APPLIED.canTransitionTo(target)) {
            return List.of(ApplicationStatus.APPLIED, target);
        }
        return List.of();
    }
}
