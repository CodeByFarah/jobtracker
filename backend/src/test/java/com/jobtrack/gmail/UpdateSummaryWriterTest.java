package com.jobtrack.gmail;

import com.jobtrack.ai.OllamaClient;
import com.jobtrack.dto.gmail.StatusChange;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.exception.AiUnavailableException;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateSummaryWriterTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private final User user = TestEntities.user(1);

    private JobApplication app(long id, String company, ApplicationStatus status, LocalDate applied, Instant updated) {
        JobApplication application = TestEntities.application(id, user, TestEntities.company(id, user, company), status);
        application.setApplicationDate(applied);
        ReflectionTestUtils.setField(application, "updatedAt", updated);
        return application;
    }

    private final List<JobApplication> applications = List.of(
            app(1, "Acme", ApplicationStatus.REJECTED, LocalDate.of(2026, 10, 1), NOW),
            app(2, "Globex", ApplicationStatus.INTERVIEW, LocalDate.of(2026, 10, 10), NOW),
            app(3, "Initech", ApplicationStatus.APPLIED, LocalDate.of(2026, 9, 1), Instant.parse("2026-09-01T09:00:00Z")));
    private final List<StatusChange> changes = List.of(
            new StatusChange(1L, "Software Engineer", "Acme", ApplicationStatus.APPLIED, ApplicationStatus.REJECTED,
                    "Acme is not moving forward."));

    @Test
    void factsListChangesCountsAndApplicationsWithoutReply() {
        String facts = UpdateSummaryWriter.facts(applications, changes, 4, NOW);

        assertThat(facts)
                .contains("Job-related emails read in this check: 4")
                .contains("Applications tracked: 3 (1 applied, 1 interview, 1 rejected)")
                .contains("Acme, Software Engineer: Applied -> Rejected (Acme is not moving forward.)")
                .contains("No reply for more than 14 days: Initech");
    }

    @Test
    void usesTheModelsSummaryWhenAvailable() {
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.chatForJson(anyString(), anyString(), any()))
                .thenReturn(JsonMapper.builder().build().readTree("{\"summary\":\"Acme turned you down.\"}"));

        assertThat(new UpdateSummaryWriter(ollama).write(applications, changes, 4, NOW)).isEqualTo("Acme turned you down.");
    }

    @Test
    void fallsBackToAPlainSummaryWhenTheModelIsDown() {
        OllamaClient ollama = mock(OllamaClient.class);
        when(ollama.chatForJson(anyString(), anyString(), any()))
                .thenThrow(new AiUnavailableException("down", null));

        assertThat(new UpdateSummaryWriter(ollama).write(applications, changes, 4, NOW))
                .isEqualTo("Acme moved to rejected. You are tracking 3 applications: 1 applied, 1 interview, 1 rejected."
                        + " 1 has had no reply for over two weeks.");
    }

    @Test
    void plainSummaryWithNothingNew() {
        assertThat(UpdateSummaryWriter.plainSummary(List.of(), List.of(), 0, NOW))
                .isEqualTo("No new job emails since the last check.");
    }
}
