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
        // Newest first; the shop email never reached the AI because it mentions no tracked company.
        getJson("/api/gmail/events", token)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyName").value("Globex"));
        verify(classifier, never()).classify(argThat(m -> m != null && m.id().equals("m-spam")), any());
        getJson("/api/gmail", token)
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRun.statusesUpdated").value(2));

        // A second run sees the same ids again but doesn't re-read or re-apply them.
        syncService.syncNow(userId);
        verify(classifier, times(2)).classify(any(), any());
        verify(gmail, times(1)).getMessage("access", "m-old");
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
