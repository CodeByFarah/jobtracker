package com.jobtrack.gmail;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmailFilterTest {

    private static GmailMessage email(String from, String subject, String body) {
        return new GmailMessage("m", from, subject, Instant.parse("2026-10-01T10:00:00Z"), body);
    }

    @Test
    void applicationEmailsFromAnyCompanyPass() {
        assertThat(JobEmailFilter.looksJobRelated(email("LinkedIn <jobs-noreply@linkedin.com>",
                "Your application was sent to Initech", "Good luck!"))).isTrue();
        assertThat(JobEmailFilter.looksJobRelated(email("hr@small-agency.com",
                "Thanks for your interest", "We'd like to invite you to an interview next week."))).isTrue();
        assertThat(JobEmailFilter.looksJobRelated(email("Initech <no-reply@us.greenhouse-mail.io>",
                "Update", "Hello"))).isTrue();
    }

    @Test
    void turkishApplicationEmailsPass() {
        assertThat(JobEmailFilter.looksJobRelated(email("ik@firma.com.tr",
                "Başvurunuz hakkında", "Başvurunuz için teşekkür ederiz."))).isTrue();
        assertThat(JobEmailFilter.looksJobRelated(email("ik@firma.com.tr",
                "Mülakat daveti", "Sizi görüşmeye davet etmek isteriz."))).isTrue();
    }

    @Test
    void ordinaryMailIsSkipped() {
        assertThat(JobEmailFilter.looksJobRelated(email("deals@shop.com", "Big sale", "50% off everything.")))
                .isFalse();
        assertThat(JobEmailFilter.looksJobRelated(email("friend@gmail.com", "Dinner?", "Are you free on Friday?")))
                .isFalse();
    }

    @Test
    void gmailSearchCoversHiringSendersWordsAndTrackedCompanies() {
        String clause = JobEmailFilter.searchClause(List.of("REV Media Group", "Acme", "Acme"));

        assertThat(clause).startsWith("{").endsWith("}");
        assertThat(clause).contains("from:greenhouse.io", "application", "\"thank you for your interest\"",
                "başvuru", "\"REV Media Group\"");
        assertThat(clause.split("\"Acme\"", -1)).hasSize(2); // listed once
    }
}
