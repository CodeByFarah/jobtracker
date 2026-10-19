package com.jobtrack.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyMatcherTest {

    private final User user = TestEntities.user(1);

    private JobApplication applicationAt(long id, String companyName, String website) {
        Company company = TestEntities.company(id, user, companyName);
        company.setWebsite(website);
        return TestEntities.application(id, user, company, ApplicationStatus.APPLIED);
    }

    private static GmailMessage email(String from, String subject, String body) {
        return new GmailMessage("m1", from, subject, Instant.parse("2026-10-01T10:00:00Z"), body);
    }

    @Test
    void matchesCompanyNamedInTheSubject() {
        JobApplication acme = applicationAt(1, "Acme Corp, Inc.", null);
        JobApplication globex = applicationAt(2, "Globex", null);

        List<JobApplication> result = CompanyMatcher.candidates(
                email("no-reply@greenhouse.io", "Your application to ACME", "Thanks for applying."),
                List.of(acme, globex));

        assertThat(result).containsExactly(acme);
    }

    @Test
    void matchesSenderDomainAgainstCompanyNameOrWebsite() {
        JobApplication hire = applicationAt(1, "Deluxe Time Hire", null);
        JobApplication initech = applicationAt(2, "Initech", "https://www.initech-group.com/careers");

        assertThat(CompanyMatcher.candidates(
                email("Recruiting <jobs@deluxetimehire.com>", "Next steps", "Hello"), List.of(hire, initech)))
                .containsExactly(hire);
        assertThat(CompanyMatcher.candidates(
                email("hr@mail.initech-group.com", "Update", "Hello"), List.of(hire, initech)))
                .containsExactly(initech);
    }

    @Test
    void ignoresAccentsAndCaseButNotPartsOfOtherWords() {
        JobApplication cafe = applicationAt(1, "Café Mondial", null);
        JobApplication meta = applicationAt(2, "Meta", null);

        assertThat(CompanyMatcher.candidates(email("a@b.com", "CAFE MONDIAL interview", ""), List.of(cafe, meta)))
                .containsExactly(cafe);
        // "metadata" must not match "Meta"
        assertThat(CompanyMatcher.candidates(email("a@b.com", "Your metadata export", ""), List.of(cafe, meta)))
                .isEmpty();
    }

    @Test
    void unrelatedEmailHasNoCandidates() {
        assertThat(CompanyMatcher.candidates(email("news@shop.com", "50% off shoes", "Sale"),
                List.of(applicationAt(1, "Acme", null)))).isEmpty();
    }

    @Test
    void coreNameDropsLegalSuffixes() {
        assertThat(CompanyMatcher.coreName("Acme Corp, Inc.")).isEqualTo("acme");
        assertThat(CompanyMatcher.coreName("Yalla Habibi Ltd. Şti.")).isEqualTo("yalla habibi");
        assertThat(CompanyMatcher.coreName("Inc")).isEqualTo("inc");
    }
}
