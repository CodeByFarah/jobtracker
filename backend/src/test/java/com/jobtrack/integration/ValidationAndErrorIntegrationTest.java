package com.jobtrack.integration;

import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Error responses: status codes, the common error body, and no leaked internals. */
class ValidationAndErrorIntegrationTest extends IntegrationTestSupport {

    private String token;
    private long companyId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        companyId = createCompany(token, "Initech");
    }

    @Test
    void registrationValidatesInputAndRejectsDuplicates() throws Exception {
        postJson("/api/auth/register", null, Map.of("email", "not-an-email", "password", "short", "fullName", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("fullName")));

        String email = "dup-" + System.nanoTime() + "@example.com";
        postJson("/api/auth/register", null, Map.of("email", email, "password", "password123", "fullName", "A"))
                .andExpect(status().isCreated());
        postJson("/api/auth/register", null, Map.of("email", email.toUpperCase(), "password", "password123", "fullName", "B"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginFailuresReturn401WithoutRevealingWhichPartWasWrong() throws Exception {
        postJson("/api/auth/login", null, Map.of("email", "nobody@example.com", "password", "password123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void applicationValidation() throws Exception {
        postJson("/api/applications", token, Map.of("jobTitle", " ", "jobUrl", "ftp://example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("companyId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("jobTitle")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("jobUrl")));

        postJson("/api/applications", token, Map.of("companyId", companyId, "jobTitle", "Dev",
                "salaryMin", 100, "salaryMax", 50))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("salaryMax"));

        postJson("/api/applications", token, Map.of("companyId", companyId, "jobTitle", "Dev",
                "applicationDate", LocalDate.now().plusDays(10).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("applicationDate"));

        postJson("/api/applications", token, Map.of("companyId", companyId, "jobTitle", "Dev", "status", "HIRED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidStatusTransitionIsAConflict() throws Exception {
        long id = createApplication(token, companyId, "Dev", "APPLIED");
        patchJson("/api/applications/" + id + "/status", token, Map.of("status", "REJECTED"))
                .andExpect(status().isOk());
        patchJson("/api/applications/" + id + "/status", token, Map.of("status", "INTERVIEW"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("final status")));
        getJson("/api/applications/" + id + "/history", token)
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void interviewDateRules() throws Exception {
        long id = createApplication(token, companyId, "Dev", "APPLIED");
        String past = Instant.now().minus(2, ChronoUnit.DAYS).toString();
        String future = Instant.now().plus(2, ChronoUnit.DAYS).toString();

        postJson("/api/applications/" + id + "/interviews", token, Map.of("type", "TECHNICAL", "scheduledAt", past))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("scheduledAt"));
        // Logging a past interview is fine when it is marked completed
        postJson("/api/applications/" + id + "/interviews", token,
                Map.of("type", "TECHNICAL", "scheduledAt", past, "status", "COMPLETED"))
                .andExpect(status().isCreated());
        postJson("/api/applications/" + id + "/interviews", token,
                Map.of("type", "TECHNICAL", "scheduledAt", future, "status", "COMPLETED"))
                .andExpect(status().isBadRequest());
        postJson("/api/applications/" + id + "/interviews", token, Map.of("scheduledAt", future))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));

        patchJson("/api/applications/" + id + "/status", token, Map.of("status", "WITHDRAWN"));
        postJson("/api/applications/" + id + "/interviews", token, Map.of("type", "FINAL", "scheduledAt", future))
                .andExpect(status().isConflict());
    }

    @Test
    void companyWithApplicationsCannotBeDeleted() throws Exception {
        long appId = createApplication(token, companyId, "Dev", "SAVED");
        deleteJson("/api/companies/" + companyId, token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("1 application")));

        deleteJson("/api/applications/" + appId, token).andExpect(status().isNoContent());
        deleteJson("/api/companies/" + companyId, token).andExpect(status().isNoContent());
        getJson("/api/companies/" + companyId, token).andExpect(status().isNotFound());
    }

    @Test
    void duplicateCompanyNameIsAConflict() throws Exception {
        postJson("/api/companies", token, Map.of("name", "initech")).andExpect(status().isConflict());
    }

    @Test
    void malformedRequestsGetAClearMessageWithoutInternals() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("Exception"))))
                .andExpect(jsonPath("$.trace").doesNotExist());

        getJson("/api/applications/abc", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("abc")));

        getJson("/api/applications/999999999", token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
