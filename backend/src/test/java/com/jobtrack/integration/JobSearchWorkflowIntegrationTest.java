package com.jobtrack.integration;

import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The acceptance workflow from the project brief, end to end through the HTTP API and PostgreSQL:
 * register, log in, create company, create application, change status, view history, schedule an
 * interview, create and complete a task, view the dashboard, search/filter.
 */
class JobSearchWorkflowIntegrationTest extends IntegrationTestSupport {

    @Test
    void completeJobSearchWorkflow() throws Exception {
        // Register, then log in with the same credentials (email is case-insensitive)
        String email = "workflow-" + System.nanoTime() + "@example.com";
        postJson("/api/auth/register", null, Map.of("email", email, "password", "password123", "fullName", "Ada"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.email").value(email));
        String token = json(postJson("/api/auth/login", null, Map.of("email", email.toUpperCase(), "password", "password123"))
                .andExpect(status().isOk())).get("accessToken").asString();

        getJson("/api/users/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ada"));

        // Company
        JsonNode company = json(postJson("/api/companies", token,
                Map.of("name", "Google", "website", "https://careers.google.com", "industry", "Technology"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location")));
        long companyId = company.get("id").asLong();

        // Application
        JsonNode application = json(postJson("/api/applications", token, Map.of(
                "companyId", companyId,
                "jobTitle", "Software Engineer",
                "location", "London",
                "employmentType", "FULL_TIME",
                "status", "APPLIED"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.company.name").value("Google"))
                .andExpect(jsonPath("$.applicationDate", notNullValue())));
        long applicationId = application.get("id").asLong();

        getJson("/api/applications/" + applicationId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Software Engineer"));

        // Status changes and history
        patchJson("/api/applications/" + applicationId + "/status", token, Map.of("status", "SCREENING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCREENING"));
        patchJson("/api/applications/" + applicationId + "/status", token, Map.of("status", "INTERVIEW"))
                .andExpect(status().isOk());

        getJson("/api/applications/" + applicationId + "/history", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].previousStatus").doesNotExist())
                .andExpect(jsonPath("$[0].newStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].previousStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].newStatus").value("SCREENING"))
                .andExpect(jsonPath("$[2].previousStatus").value("SCREENING"))
                .andExpect(jsonPath("$[2].newStatus").value("INTERVIEW"));

        // Interview
        Instant inThreeDays = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        postJson("/api/applications/" + applicationId + "/interviews", token, Map.of(
                "type", "TECHNICAL", "scheduledAt", inThreeDays.toString(), "interviewerName", "Grace"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.companyName").value("Google"));

        // Follow-up task, then complete it
        long taskId = json(postJson("/api/applications/" + applicationId + "/tasks", token, Map.of(
                "title", "Prepare for interview", "dueDate", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.completed").value(false))).get("id").asLong();

        getJson("/api/tasks?completed=false", token).andExpect(jsonPath("$", hasSize(1)));

        patchJson("/api/tasks/" + taskId + "/completion", token, Map.of("completed", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt", notNullValue()));

        getJson("/api/tasks?completed=false", token).andExpect(jsonPath("$", hasSize(0)));
        getJson("/api/tasks?completed=true", token).andExpect(jsonPath("$", hasSize(1)));

        // Dashboard reflects the real data
        JsonNode dashboard = json(getJson("/api/dashboard", token).andExpect(status().isOk()));
        assertThat(dashboard.get("totalApplications").asLong()).isEqualTo(1);
        assertThat(dashboard.get("activeApplications").asLong()).isEqualTo(1);
        assertThat(dashboard.get("totalInterviews").asLong()).isEqualTo(1);
        assertThat(dashboard.get("upcomingInterviewCount").asLong()).isEqualTo(1);
        assertThat(dashboard.get("outstandingTasks").asLong()).isZero();
        assertThat(dashboard.get("offers").asLong()).isZero();
        assertThat(dashboard.get("upcomingInterviews")).hasSize(1);
        assertThat(dashboard.get("statusBreakdown")).hasSize(8);
        assertThat(countFor(dashboard, "INTERVIEW")).isEqualTo(1);

        // Search / filter
        getJson("/api/applications?q=google&status=INTERVIEW", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(applicationId));
        getJson("/api/applications?status=REJECTED", token)
                .andExpect(jsonPath("$.totalElements").value(0));

        // Progress to an offer: the dashboard counts it via the status history
        patchJson("/api/applications/" + applicationId + "/status", token, Map.of("status", "OFFER"))
                .andExpect(status().isOk());
        patchJson("/api/applications/" + applicationId + "/status", token, Map.of("status", "ACCEPTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions", hasSize(0)));
        JsonNode finalDashboard = json(getJson("/api/dashboard", token));
        assertThat(finalDashboard.get("offers").asLong()).isEqualTo(1);
        assertThat(finalDashboard.get("activeApplications").asLong()).isZero();
    }

    private static long countFor(JsonNode dashboard, String status) {
        for (JsonNode entry : dashboard.get("statusBreakdown")) {
            if (entry.get("status").asString().equals(status)) {
                return entry.get("count").asLong();
            }
        }
        throw new AssertionError("status missing from breakdown: " + status);
    }
}
