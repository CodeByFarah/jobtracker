package com.jobtrack.support;

import com.jobtrack.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests: full application context, real HTTP pipeline via MockMvc (including
 * the security filter chain) and a PostgreSQL Testcontainer. Each test registers fresh users with
 * unique emails, so tests do not interfere with each other even though they share the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    /** Registers a new user and returns their access token. */
    protected String registerUser() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        JsonNode body = json(perform(post("/api/auth/register"), null,
                Map.of("email", email, "password", "password123", "fullName", "Test User"))
                .andExpect(status().isCreated()));
        return body.get("accessToken").asString();
    }

    protected long createCompany(String token, String name) throws Exception {
        return json(perform(post("/api/companies"), token, Map.of("name", name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createApplication(String token, long companyId, String jobTitle, String status) throws Exception {
        return json(perform(post("/api/applications"), token,
                Map.of("companyId", companyId, "jobTitle", jobTitle, "status", status))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected ResultActions getJson(String url, String token) throws Exception {
        return perform(get(url), token, null);
    }

    protected ResultActions postJson(String url, String token, Object body) throws Exception {
        return perform(post(url), token, body);
    }

    protected ResultActions putJson(String url, String token, Object body) throws Exception {
        return perform(put(url), token, body);
    }

    protected ResultActions patchJson(String url, String token, Object body) throws Exception {
        return perform(patch(url), token, body);
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        return perform(delete(url), token, null);
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }
}
