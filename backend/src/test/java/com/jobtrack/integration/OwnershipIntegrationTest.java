package com.jobtrack.integration;

import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User B must not be able to read or change anything belonging to user A. The API answers 404
 * (rather than 403) so it does not even confirm that the resource exists.
 */
class OwnershipIntegrationTest extends IntegrationTestSupport {

    private String ownerToken;
    private String otherToken;
    private long companyId;
    private long applicationId;
    private long interviewId;
    private long taskId;

    @BeforeEach
    void setUp() throws Exception {
        ownerToken = registerUser();
        otherToken = registerUser();
        companyId = createCompany(ownerToken, "Owner Co");
        applicationId = createApplication(ownerToken, companyId, "Engineer", "APPLIED");
        interviewId = json(postJson("/api/applications/" + applicationId + "/interviews", ownerToken, Map.of(
                "type", "RECRUITER",
                "scheduledAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()))
                .andExpect(status().isCreated())).get("id").asLong();
        taskId = json(postJson("/api/applications/" + applicationId + "/tasks", ownerToken, Map.of("title", "Follow up"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void otherUserCannotReadOrModifyApplication() throws Exception {
        getJson("/api/applications/" + applicationId, otherToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
        putJson("/api/applications/" + applicationId, otherToken, Map.of("companyId", companyId, "jobTitle", "Hacked"))
                .andExpect(status().isNotFound());
        patchJson("/api/applications/" + applicationId + "/status", otherToken, Map.of("status", "REJECTED"))
                .andExpect(status().isNotFound());
        getJson("/api/applications/" + applicationId + "/history", otherToken).andExpect(status().isNotFound());
        deleteJson("/api/applications/" + applicationId, otherToken).andExpect(status().isNotFound());

        // Owner's data is untouched
        getJson("/api/applications/" + applicationId, ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Engineer"))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void otherUserCannotAccessCompany() throws Exception {
        getJson("/api/companies/" + companyId, otherToken).andExpect(status().isNotFound());
        getJson("/api/companies/" + companyId + "/applications", otherToken).andExpect(status().isNotFound());
        putJson("/api/companies/" + companyId, otherToken, Map.of("name", "Mine now")).andExpect(status().isNotFound());
        deleteJson("/api/companies/" + companyId, otherToken).andExpect(status().isNotFound());
        getJson("/api/companies", otherToken).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void otherUserCannotAttachApplicationToForeignCompany() throws Exception {
        postJson("/api/applications", otherToken, Map.of("companyId", companyId, "jobTitle", "Sneaky"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUserCannotAccessInterviewsOrTasks() throws Exception {
        getJson("/api/applications/" + applicationId + "/interviews", otherToken).andExpect(status().isNotFound());
        postJson("/api/applications/" + applicationId + "/interviews", otherToken, Map.of(
                "type", "OTHER", "scheduledAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()))
                .andExpect(status().isNotFound());
        putJson("/api/interviews/" + interviewId, otherToken, Map.of(
                "type", "OTHER", "scheduledAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()))
                .andExpect(status().isNotFound());
        deleteJson("/api/interviews/" + interviewId, otherToken).andExpect(status().isNotFound());

        getJson("/api/applications/" + applicationId + "/tasks", otherToken).andExpect(status().isNotFound());
        patchJson("/api/tasks/" + taskId + "/completion", otherToken, Map.of("completed", true))
                .andExpect(status().isNotFound());
        deleteJson("/api/tasks/" + taskId, otherToken).andExpect(status().isNotFound());

        getJson("/api/interviews", otherToken).andExpect(jsonPath("$", hasSize(0)));
        getJson("/api/tasks", otherToken).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listAndDashboardOnlyIncludeOwnData() throws Exception {
        getJson("/api/applications", otherToken).andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/dashboard", otherToken)
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.totalInterviews").value(0))
                .andExpect(jsonPath("$.outstandingTasks").value(0));
    }

    @Test
    void requestsWithoutValidTokenAreRejected() throws Exception {
        getJson("/api/applications/" + applicationId, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        getJson("/api/dashboard", "not-a-real-token").andExpect(status().isUnauthorized());
    }
}
