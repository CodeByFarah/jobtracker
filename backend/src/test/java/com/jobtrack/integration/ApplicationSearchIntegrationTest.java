package com.jobtrack.integration;

import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Filtering, sorting and pagination are executed by the database query. */
class ApplicationSearchIntegrationTest extends IntegrationTestSupport {

    private String token;
    private long acmeId;

    /**
     * 12 applications: 7 at Acme (5 APPLIED + 2 REJECTED, 3 in Berlin), 5 at Globex (all APPLIED,
     * CONTRACT, remote). Application dates are distinct so date sorting is deterministic.
     */
    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        acmeId = createCompany(token, "Acme");
        long globexId = createCompany(token, "Globex");
        LocalDate start = LocalDate.now().minusDays(30);
        for (int i = 0; i < 7; i++) {
            long id = create(acmeId, "Backend Engineer " + i, i < 3 ? "Berlin" : "Munich", "FULL_TIME", start.plusDays(i));
            if (i >= 5) {
                patchJson("/api/applications/" + id + "/status", token, Map.of("status", "REJECTED"))
                        .andExpect(status().isOk());
            }
        }
        for (int i = 0; i < 5; i++) {
            create(globexId, "Frontend Developer " + i, "Remote", "CONTRACT", start.plusDays(10 + i));
        }
    }

    @Test
    void pageSizeAndPageNumberWork() throws Exception {
        getJson("/api/applications?size=5&page=0", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));
        getJson("/api/applications?size=5&page=2", token)
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(2));
        getJson("/api/applications?size=5&page=3", token)
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void pagesDoNotOverlapAndCoverEveryRecord() throws Exception {
        Set<Long> seen = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            for (JsonNode item : json(getJson("/api/applications?size=5&page=" + page, token)).get("content")) {
                assertThat(seen.add(item.get("id").asLong())).as("duplicate across pages").isTrue();
            }
        }
        assertThat(seen).hasSize(12);
    }

    @Test
    void filtersCombineWithPagination() throws Exception {
        // Acme + APPLIED = 5 applications, fetched 2 per page
        getJson("/api/applications?companyId=" + acmeId + "&status=APPLIED&size=2&page=0", token)
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)));
        getJson("/api/applications?companyId=" + acmeId + "&status=APPLIED&size=2&page=2", token)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("APPLIED"));
    }

    @Test
    void searchMatchesJobTitleOrCompanyNameCaseInsensitively() throws Exception {
        getJson("/api/applications?q=FRONTEND", token).andExpect(jsonPath("$.totalElements").value(5));
        getJson("/api/applications?q=acme", token).andExpect(jsonPath("$.totalElements").value(7));
        // (MockMvc URL templates encode the query string themselves, so it is written unencoded here)
        getJson("/api/applications?q=engineer 3", token).andExpect(jsonPath("$.totalElements").value(1));
        // LIKE wildcards in the search text are treated literally
        getJson("/api/applications?q=%", token).andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/applications?q=_", token).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByLocationEmploymentTypeAndStatus() throws Exception {
        getJson("/api/applications?location=berl", token).andExpect(jsonPath("$.totalElements").value(3));
        getJson("/api/applications?employmentType=CONTRACT", token).andExpect(jsonPath("$.totalElements").value(5));
        getJson("/api/applications?status=REJECTED", token).andExpect(jsonPath("$.totalElements").value(2));
        getJson("/api/applications?q=engineer&location=munich&status=APPLIED", token)
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void sortsByApplicationDateInBothDirections() throws Exception {
        List<String> desc = dates(json(getJson("/api/applications?sort=APPLICATION_DATE&direction=DESC&size=100", token)));
        assertThat(desc).isSortedAccordingTo((a, b) -> b.compareTo(a));
        List<String> asc = dates(json(getJson("/api/applications?sort=APPLICATION_DATE&direction=ASC&size=100", token)));
        assertThat(asc).isSorted();
    }

    @Test
    void sortsByUpdatedAt() throws Exception {
        // Touch the oldest application; it must then come first when sorting by last update
        long oldestId = json(getJson("/api/applications?sort=APPLICATION_DATE&direction=ASC&size=1", token))
                .get("content").get(0).get("id").asLong();
        patchJson("/api/applications/" + oldestId + "/status", token, Map.of("status", "SCREENING"))
                .andExpect(status().isOk());

        JsonNode page = json(getJson("/api/applications?sort=UPDATED_AT&direction=DESC&size=1", token));
        assertThat(page.get("content").get(0).get("id").asLong()).isEqualTo(oldestId);
    }

    @Test
    void rejectsInvalidPagingAndSortParameters() throws Exception {
        getJson("/api/applications?size=0", token).andExpect(status().isBadRequest());
        getJson("/api/applications?size=101", token).andExpect(status().isBadRequest());
        getJson("/api/applications?page=-1", token).andExpect(status().isBadRequest());
        getJson("/api/applications?sort=password", token).andExpect(status().isBadRequest());
        getJson("/api/applications?status=NOT_A_STATUS", token).andExpect(status().isBadRequest());
    }

    private long create(long companyId, String title, String location, String type, LocalDate date) throws Exception {
        return json(postJson("/api/applications", token, Map.of(
                "companyId", companyId, "jobTitle", title, "location", location,
                "employmentType", type, "status", "APPLIED", "applicationDate", date.toString()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static List<String> dates(JsonNode page) {
        List<String> dates = new ArrayList<>();
        page.get("content").forEach(item -> dates.add(item.get("applicationDate").asString()));
        return dates;
    }
}
