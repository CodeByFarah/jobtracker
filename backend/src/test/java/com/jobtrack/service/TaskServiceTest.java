package com.jobtrack.service;

import com.jobtrack.dto.task.CreateTaskRequest;
import com.jobtrack.dto.task.TaskResponse;
import com.jobtrack.dto.task.UpdateTaskRequest;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.FollowUpTask;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.FollowUpTaskRepository;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @Mock
    FollowUpTaskRepository taskRepository;
    @Mock
    ApplicationService applicationService;

    TaskService service;
    JobApplication application;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, applicationService, Clock.fixed(NOW, ZoneOffset.UTC));
        User user = TestEntities.user(USER_ID);
        application = TestEntities.application(5L, user, TestEntities.company(2L, user, "Acme"), ApplicationStatus.APPLIED);
    }

    @Test
    void createsOpenTaskForOwnedApplication() {
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenReturn(application);
        when(taskRepository.save(any(FollowUpTask.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = service.create(USER_ID, 5L,
                new CreateTaskRequest(" Follow up with recruiter ", "", LocalDate.of(2026, 9, 20)));

        assertThat(response.title()).isEqualTo("Follow up with recruiter");
        assertThat(response.description()).isNull();
        assertThat(response.completed()).isFalse();
        assertThat(response.completedAt()).isNull();
        assertThat(response.jobTitle()).isEqualTo("Software Engineer");
    }

    @Test
    void markingCompleteStoresTheCompletionTimestamp() {
        FollowUpTask task = TestEntities.withId(new FollowUpTask(application, "Send thank-you email"), 9L);
        when(taskRepository.findByIdAndApplicationUserId(9L, USER_ID)).thenReturn(Optional.of(task));

        TaskResponse response = service.setCompleted(USER_ID, 9L, true);

        assertThat(response.completed()).isTrue();
        assertThat(response.completedAt()).isEqualTo(NOW);
    }

    @Test
    void reopeningViaUpdateClearsTheCompletionTimestamp() {
        FollowUpTask task = TestEntities.withId(new FollowUpTask(application, "Send thank-you email"), 9L);
        task.setCompleted(true, NOW.minusSeconds(3600));
        when(taskRepository.findByIdAndApplicationUserId(9L, USER_ID)).thenReturn(Optional.of(task));

        TaskResponse response = service.update(USER_ID, 9L,
                new UpdateTaskRequest("Send thank-you email", "to both interviewers", null, false));

        assertThat(response.completed()).isFalse();
        assertThat(response.completedAt()).isNull();
        assertThat(response.description()).isEqualTo("to both interviewers");
    }

    @Test
    void anotherUsersTaskIsNotFound() {
        when(taskRepository.findByIdAndApplicationUserId(9L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCompleted(USER_ID, 9L, true)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(USER_ID, 9L)).isInstanceOf(ResourceNotFoundException.class);
        verify(taskRepository, never()).delete(any());
    }
}
