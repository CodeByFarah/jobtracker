package com.jobtrack.gmail;

import com.jobtrack.entity.EmailCategory;
import com.jobtrack.entity.GmailConnection;
import com.jobtrack.repository.GmailConnectionRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole sync against a real database, with Gmail and the AI replaced by fakes: emails are
 * matched to applications, statuses move forward with history, and nothing is processed twice.
 */
class EmailSyncIntegrationTest extends IntegrationTestSupport {

    @MockitoBean
    GmailApiClient gmail;
    @MockitoBean
    EmailClassifier classifier;
    @MockitoBean
    UpdateSummaryWriter summaryWriter;

    @Autowired
    EmailSyncService syncService;
    @Autowired
    GmailConnectionRepository connectionRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    TokenCipher tokenCipher;

    @Test
    void syncUpdatesMatchingApplicationsAndRecordsTheEmails() throws Exception {
        String token = registerUser();
        long userId = json(getJson("/api/users/me", token)).get("id").asLong();
        long acme = createApplication(token, createCompany(token, "Acme"), "Backend Engineer", "APPLIED");
        long globex = createApplication(token, createCompany(token, "Globex"), "Designer", "SAVED");

        GmailConnection connection = new GmailConnection(userRepository.getReferenceById(userId));
        connection.setGmailAddress("me@gmail.com");
        connection.setRefreshTokenEncrypted(tokenCipher.encrypt("refresh-" + userId));
        connectionRepository.save(connection);

        when(gmail.refresh("refresh-" + userId)).thenReturn(new GmailApiClient.Tokens("access", null, 3600));
        when(gmail.listMessageIds(eq("access"), anyString(), anyInt())).thenReturn(List.of("m-new", "m-old", "m-spam"));
        when(gmail.getMessage("access", "m-old")).thenReturn(new GmailMessage("m-old", "jobs@acme.io",
                "Your Acme application", Instant.parse("2026-10-01T09:00:00Z"), "We decided not to move forward."));
        when(gmail.getMessage("access", "m-new")).thenReturn(new GmailMessage("m-new", "maria@globex.com",
                "Globex interview", Instant.parse("2026-10-05T09:00:00Z"), "Let's schedule a call."));
        when(gmail.getMessage("access", "m-spam")).thenReturn(new GmailMessage("m-spam", "deals@shop.com",
                "Big sale", Instant.parse("2026-10-03T09:00:00Z"), "50% off everything."));
        when(classifier.classify(argThat(m -> m != null && m.id().equals("m-old")), any()))
                .thenReturn(new EmailClassification(acme, EmailCategory.REJECTED, 0.95, "Acme said no."));
        when(classifier.classify(argThat(m -> m != null && m.id().equals("m-new")), any()))
                .thenReturn(new EmailClassification(globex, EmailCategory.INTERVIEW, 0.9, "Globex wants a call."));

        when(summaryWriter.write(any(), any(), anyInt(), any())).thenReturn("Acme said no; Globex wants to talk.");
        syncService.syncNow(userId);

        getJson("/api/applications/" + acme, token).andExpect(jsonPath("$.status").value("REJECTED"));
        // SAVED goes through APPLIED first, so the history stays a valid path.
        getJson("/api/applications/" + globex, token).andExpect(jsonPath("$.status").value("INTERVIEW"));
        getJson("/api/applications/" + globex + "/history", token)
                .andExpect(jsonPath("$[*].newStatus").value(org.hamcrest.Matchers.contains("SAVED", "APPLIED", "INTERVIEW")));
        getJson("/api/applications/" + acme + "/emails", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("REJECTED"))
                .andExpect(jsonPath("$[0].statusBefore").value("APPLIED"))
                .andExpect(jsonPath("$[0].statusAfter").value("REJECTED"))
                .andExpect(jsonPath("$[0].summary").value("Acme said no."));
        // Newest first; the shop email never reached the AI: no tracked company and nothing job-like.
        getJson("/api/gmail/events", token)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Globex"));
        verify(classifier, never()).classify(argThat(m -> m != null && m.id().equals("m-spam")), any());
        getJson("/api/gmail", token)
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRun.statusesUpdated").value(2));
        // The report behind "Check for Updates": AI summary, every change, and every application.
        getJson("/api/gmail/report", token)
                .andExpect(jsonPath("$.lastCheck.overview").value("Acme said no; Globex wants to talk."))
                .andExpect(jsonPath("$.lastCheck.changes.length()").value(2))
                .andExpect(jsonPath("$.lastCheck.changes[*].to").value(org.hamcrest.Matchers.contains("INTERVIEW", "REJECTED"))) // newest email first
                .andExpect(jsonPath("$.applications.length()").value(2))
                .andExpect(jsonPath("$.applications[0].changedInLastCheck").value(true))
                .andExpect(jsonPath("$.applications[?(@.companyName == 'Globex')].lastEmailSummary").value(org.hamcrest.Matchers.contains("Globex wants a call.")));

        // A second run sees the same ids again but doesn't re-read or re-apply them.
        syncService.syncNow(userId);
        verify(classifier, times(2)).classify(any(), any());
        verify(gmail, times(1)).getMessage("access", "m-old");
    }

    @Test
    void emailsAboutUntrackedApplicationsAddThem() throws Exception {
        String token = registerUser();
        long userId = json(getJson("/api/users/me", token)).get("id").asLong();
        GmailConnection connection = new GmailConnection(userRepository.getReferenceById(userId));
        connection.setGmailAddress("me@gmail.com");
        connection.setRefreshTokenEncrypted(tokenCipher.encrypt("refresh-" + userId));
        connectionRepository.save(connection);

        when(gmail.refresh("refresh-" + userId)).thenReturn(new GmailApiClient.Tokens("access3", null, 3600));
        when(gmail.listMessageIds(eq("access3"), anyString(), anyInt()))
                .thenReturn(List.of("received", "interview", "alert"));
        when(gmail.getMessage("access3", "received")).thenReturn(new GmailMessage("received",
                "jobs-noreply@linkedin.com", "Your application was sent to Initech",
                Instant.parse("2026-09-20T09:00:00Z"), "Your application for Data Analyst at Initech was sent."));
        when(gmail.getMessage("access3", "interview")).thenReturn(new GmailMessage("interview",
                "no-reply@greenhouse.io", "Next steps with Initech", Instant.parse("2026-09-28T09:00:00Z"),
                "We'd love to set up a call with you."));
        when(gmail.getMessage("access3", "alert")).thenReturn(new GmailMessage("alert",
                "jobalerts@linkedin.com", "New jobs for you", Instant.parse("2026-09-25T09:00:00Z"),
                "Hooli is hiring a Product Manager. Apply now: 30 applicants."));
        when(classifier.classify(argThat(m -> m != null && m.id().equals("received")), any()))
                .thenReturn(new EmailClassification(null, EmailCategory.APPLICATION_RECEIVED, 0.95,
                        "Your Initech application was sent.", "Initech", "Data Analyst"));
        // Emails are read newest first: the invite doesn't name the job, so the application starts
        // without a title; the older confirmation then fills in the title and the real apply date.
        when(classifier.classify(argThat(m -> m != null && m.id().equals("interview")), any()))
                .thenReturn(new EmailClassification(null, EmailCategory.INTERVIEW, 0.9,
                        "Initech wants a call.", "Initech", null));
        when(classifier.classify(argThat(m -> m != null && m.id().equals("alert")), any()))
                .thenReturn(new EmailClassification(null, EmailCategory.NOT_JOB_RELATED, 0.9,
                        "A job alert.", "Hooli", "Product Manager"));
        when(summaryWriter.write(any(), any(), anyInt(), any())).thenReturn("Initech added and wants a call.");

        syncService.syncNow(userId);

        // One application, added from the inbox, moved on by the later email; the job alert added nothing.
        getJson("/api/applications", token)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company.name").value("Initech"))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Data Analyst"))
                .andExpect(jsonPath("$.content[0].status").value("INTERVIEW"))
                .andExpect(jsonPath("$.content[0].applicationDate").value("2026-09-20"));
        getJson("/api/gmail/report", token)
                .andExpect(jsonPath("$.lastCheck.emailsAnalyzed").value(3))
                .andExpect(jsonPath("$.lastCheck.changes.length()").value(1))
                .andExpect(jsonPath("$.lastCheck.changes[0].from").doesNotExist())
                .andExpect(jsonPath("$.lastCheck.changes[0].to").value("INTERVIEW"));
    }

    @Test
    void lowConfidenceIsRecordedButDoesNotChangeTheStatus() throws Exception {
        String token = registerUser();
        long userId = json(getJson("/api/users/me", token)).get("id").asLong();
        long acme = createApplication(token, createCompany(token, "Acme"), "Backend Engineer", "APPLIED");
        GmailConnection connection = new GmailConnection(userRepository.getReferenceById(userId));
        connection.setGmailAddress("me@gmail.com");
        connection.setRefreshTokenEncrypted(tokenCipher.encrypt("refresh-" + userId));
        connectionRepository.save(connection);

        when(gmail.refresh("refresh-" + userId)).thenReturn(new GmailApiClient.Tokens("access2", null, 3600));
        when(gmail.listMessageIds(eq("access2"), anyString(), anyInt())).thenReturn(List.of("m1"));
        when(gmail.getMessage("access2", "m1")).thenReturn(new GmailMessage("m1", "jobs@acme.io", "Acme",
                Instant.parse("2026-10-01T09:00:00Z"), "Maybe news?"));
        when(classifier.classify(any(), any()))
                .thenReturn(new EmailClassification(acme, EmailCategory.REJECTED, 0.3, "Unclear."));

        syncService.syncNow(userId);

        getJson("/api/applications/" + acme, token).andExpect(jsonPath("$.status").value("APPLIED"));
        getJson("/api/applications/" + acme + "/emails", token)
                .andExpect(jsonPath("$[0].category").value("REJECTED"))
                .andExpect(jsonPath("$[0].statusAfter").doesNotExist());
    }
}
