package com.jobtrack.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Which of the user's applications at a company an email is about. */
class EmailApplicationResolverTest {

    private final User user = TestEntities.user(1);
    private final Company initech = TestEntities.company(1, user, "Initech");

    private JobApplication app(long id, String title, ApplicationStatus status) {
        JobApplication application = TestEntities.application(id, user, initech, status);
        application.setJobTitle(title);
        return application;
    }

    @Test
    void withoutATitleTheMostRecentOpenApplicationIsChosen() {
        JobApplication closed = app(1, "Analyst", ApplicationStatus.REJECTED);
        JobApplication open = app(2, "Engineer", ApplicationStatus.APPLIED);

        assertThat(EmailApplicationResolver.pick(List.of(closed, open), null)).contains(open);
    }

    @Test
    void aSimilarTitleMatches() {
        JobApplication engineer = app(1, "Backend Engineer", ApplicationStatus.APPLIED);
        JobApplication analyst = app(2, "Data Analyst", ApplicationStatus.APPLIED);

        assertThat(EmailApplicationResolver.pick(List.of(engineer, analyst), "Senior Data Analyst")).contains(analyst);
        assertThat(EmailApplicationResolver.pick(List.of(engineer, analyst), "backend engineer")).contains(engineer);
    }

    @Test
    void aDifferentJobAtTheSameCompanyIsANewApplication() {
        JobApplication engineer = app(1, "Backend Engineer", ApplicationStatus.APPLIED);

        assertThat(EmailApplicationResolver.pick(List.of(engineer), "Product Designer")).isEmpty();
    }

    @Test
    void anApplicationAddedWithoutATitleTakesTheNextNamedJob() {
        JobApplication unnamed = app(1, EmailApplicationResolver.UNKNOWN_TITLE, ApplicationStatus.APPLIED);

        assertThat(EmailApplicationResolver.pick(List.of(unnamed), "Product Designer")).contains(unnamed);
    }

    @Test
    void noApplicationsAtTheCompanyMeansNothingToPick() {
        assertThat(EmailApplicationResolver.pick(List.of(), "Anything")).isEmpty();
    }
}
