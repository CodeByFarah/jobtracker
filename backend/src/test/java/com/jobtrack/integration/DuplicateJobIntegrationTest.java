package com.jobtrack.integration;

import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A job can only be tracked once: the same posting link is refused, however it was shared. */
class DuplicateJobIntegrationTest extends IntegrationTestSupport {

    private static Map<String, Object> job(long companyId, String title, String url) {
        return Map.of("companyId", companyId, "jobTitle", title, "jobUrl", url, "status", "APPLIED");
    }

    @Test
    void theSameJobLinkCannotBeAddedTwice() throws Exception {
        String token = registerUser();
        long acme = createCompany(token, "Acme");
        postJson("/api/applications", token, job(acme, "Backend Engineer", "https://jobs.lever.co/acme/123"))
                .andExpect(status().isCreated());

        // Same posting, shared with tracking parameters and a trailing slash.
        postJson("/api/applications", token,
                job(acme, "Backend Engineer", "https://jobs.lever.co/acme/123/?lever-source=LinkedIn"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already added this job: Backend Engineer at Acme")));

        // A different posting is fine, and so is the same link for another user.
        postJson("/api/applications", token, job(acme, "Data Engineer", "https://jobs.lever.co/acme/456"))
                .andExpect(status().isCreated());
        String other = registerUser();
        postJson("/api/applications", other,
                job(createCompany(other, "Acme"), "Backend Engineer", "https://jobs.lever.co/acme/123"))
                .andExpect(status().isCreated());
    }

    @Test
    void editingCannotTurnAJobIntoACopyOfAnother() throws Exception {
        String token = registerUser();
        long acme = createCompany(token, "Acme");
        postJson("/api/applications", token, job(acme, "Backend Engineer", "https://jobs.lever.co/acme/1"))
                .andExpect(status().isCreated());
        long second = json(postJson("/api/applications", token, job(acme, "Data Engineer", "https://jobs.lever.co/acme/2")))
                .get("id").asLong();

        putJson("/api/applications/" + second, token,
                Map.of("companyId", acme, "jobTitle", "Data Engineer", "jobUrl", "https://jobs.lever.co/acme/1"))
                .andExpect(status().isConflict());
        // Saving it with its own link is still fine.
        putJson("/api/applications/" + second, token,
                Map.of("companyId", acme, "jobTitle", "Data Engineer II", "jobUrl", "https://jobs.lever.co/acme/2"))
                .andExpect(status().isOk());
    }
}
