package com.jobtrack.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.JobApplication;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** An application counts as "no reply" when it is still waiting and nothing has happened for 14 days. */
public final class NoReplyRule {

    public static final Duration WAIT = Duration.ofDays(14);

    private NoReplyRule() {
    }

    public static boolean applies(JobApplication application, Instant now) {
        if (application.getStatus() != ApplicationStatus.APPLIED && application.getStatus() != ApplicationStatus.SCREENING) {
            return false;
        }
        Instant applied = application.getApplicationDate() == null ? Instant.EPOCH
                : application.getApplicationDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant updated = application.getUpdatedAt() == null ? Instant.EPOCH : application.getUpdatedAt();
        Instant lastActivity = applied.isAfter(updated) ? applied : updated;
        return lastActivity.isBefore(now.minus(WAIT));
    }
}
