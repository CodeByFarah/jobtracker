package com.jobtrack.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;
import org.junit.jupiter.api.Test;

import static com.jobtrack.entity.ApplicationStatus.APPLIED;
import static com.jobtrack.entity.ApplicationStatus.INTERVIEW;
import static com.jobtrack.entity.ApplicationStatus.OFFER;
import static com.jobtrack.entity.ApplicationStatus.REJECTED;
import static com.jobtrack.entity.ApplicationStatus.SAVED;
import static com.jobtrack.entity.ApplicationStatus.SCREENING;
import static org.assertj.core.api.Assertions.assertThat;

class EmailStatusRulesTest {

    @Test
    void rejectionEmailClosesAnAppliedApplication() {
        assertThat(EmailStatusRules.steps(APPLIED, EmailCategory.REJECTED)).containsExactly(REJECTED);
    }

    @Test
    void interviewInviteMovesAppliedToInterview() {
        assertThat(EmailStatusRules.steps(APPLIED, EmailCategory.INTERVIEW)).containsExactly(INTERVIEW);
    }

    @Test
    void savedApplicationFirstMovesToAppliedSoTheHistoryStaysValid() {
        assertThat(EmailStatusRules.steps(SAVED, EmailCategory.INTERVIEW)).containsExactly(APPLIED, INTERVIEW);
        assertThat(EmailStatusRules.steps(SAVED, EmailCategory.REJECTED)).containsExactly(APPLIED, REJECTED);
        assertThat(EmailStatusRules.steps(SAVED, EmailCategory.APPLICATION_RECEIVED)).containsExactly(APPLIED);
    }

    @Test
    void anAssessmentMeansScreening() {
        assertThat(EmailStatusRules.steps(APPLIED, EmailCategory.ASSESSMENT)).containsExactly(SCREENING);
    }

    @Test
    void oldEmailsNeverMoveAnApplicationBackwards() {
        assertThat(EmailStatusRules.steps(INTERVIEW, EmailCategory.APPLICATION_RECEIVED)).isEmpty();
        assertThat(EmailStatusRules.steps(INTERVIEW, EmailCategory.ASSESSMENT)).isEmpty();
        assertThat(EmailStatusRules.steps(OFFER, EmailCategory.INTERVIEW)).isEmpty();
    }

    @Test
    void sameStatusOrClosedApplicationIsLeftAlone() {
        assertThat(EmailStatusRules.steps(INTERVIEW, EmailCategory.INTERVIEW)).isEmpty();
        assertThat(EmailStatusRules.steps(REJECTED, EmailCategory.OFFER)).isEmpty();
    }

    @Test
    void emailsThatDontImplyAStatusChangeNothing() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(EmailStatusRules.steps(status, EmailCategory.OTHER)).isEmpty();
            assertThat(EmailStatusRules.steps(status, EmailCategory.NOT_JOB_RELATED)).isEmpty();
        }
    }
}
