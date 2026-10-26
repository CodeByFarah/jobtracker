package com.jobtrack.integration;

import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gmail endpoints without Google credentials configured (the default for a fresh checkout).
 * The credentials are blanked explicitly so a developer's local .env doesn't change the result.
 */
@TestPropertySource(properties = {"GOOGLE_CLIENT_ID=", "GOOGLE_CLIENT_SECRET="})
class GmailEndpointsIntegrationTest extends IntegrationTestSupport {

    @Test
    void statusShowsNotConfiguredAndNotConnected() throws Exception {
        String token = registerUser();

        getJson("/api/gmail", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.syncing").value(false));
        getJson("/api/gmail/connect-url", token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("isn't available")));
        postJson("/api/gmail/sync", token, Map.of()).andExpect(status().isNotFound());
        getJson("/api/gmail/events", token).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void endpointsRequireLoginExceptTheOAuthCallback() throws Exception {
        getJson("/api/gmail", null).andExpect(status().isUnauthorized());
        postJson("/api/applications/import-link", null, Map.of("url", "https://example.com")).andExpect(status().isUnauthorized());

        // A forged state never connects anything; the browser is sent back to the profile page with an error.
        mockMvc.perform(get("/api/gmail/oauth/callback").param("code", "abc").param("state", "forged"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/gmail/done?gmail=error")));
    }

    @Test
    void applicationEmailsAreScopedToTheOwner() throws Exception {
        String owner = registerUser();
        long applicationId = createApplication(owner, createCompany(owner, "Acme"), "Engineer", "APPLIED");

        getJson("/api/applications/" + applicationId + "/emails", owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        getJson("/api/applications/" + applicationId + "/emails", registerUser()).andExpect(status().isNotFound());
    }

    @Test
    void importLinkRejectsInvalidAndPrivateAddresses() throws Exception {
        String token = registerUser();

        postJson("/api/applications/import-link", token, Map.of("url", "not a link"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"));
        postJson("/api/applications/import-link", token, Map.of("url", "http://127.0.0.1:8080/api/users/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("private network")));
    }
}
