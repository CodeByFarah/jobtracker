package com.jobtrack.service;

import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.Interview;
import com.jobtrack.entity.InterviewStatus;
import com.jobtrack.entity.InterviewType;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private static final long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
    private static final Instant YESTERDAY = NOW.minus(Duration.ofDays(1));

    @Mock
    InterviewRepository interviewRepository;
    @Mock
    ApplicationService applicationService;

    InterviewService service;
    JobApplication application;

    @BeforeEach
    void setUp() {
        service = new InterviewService(interviewRepository, applicationService, Clock.fixed(NOW, ZoneOffset.UTC));
        User user = TestEntities.user(USER_ID);
        application = TestEntities.application(5L, user, TestEntities.company(2L, user, "Acme"), ApplicationStatus.APPLIED);
    }

    @Test
    void schedulesInterviewInTheFutureWithDefaultStatus() {
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenReturn(application);
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewResponse response = service.create(USER_ID, 5L, request(TOMORROW, null));

        assertThat(response.status()).isEqualTo(InterviewStatus.SCHEDULED);
        assertThat(response.applicationId()).isEqualTo(5L);
        assertThat(response.companyName()).isEqualTo("Acme");
        assertThat(response.interviewerName()).isEqualTo("Grace");
    }

    @Test
    void rejectsScheduledInterviewInThePast() {
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenReturn(application);
        assertThatThrownBy(() -> service.create(USER_ID, 5L, request(YESTERDAY, InterviewStatus.SCHEDULED)))
                .isInstanceOf(BadRequestException.class)
                .extracting("field").isEqualTo("scheduledAt");
        verify(interviewRepository, never()).save(any());
    }

    @Test
    void allowsLoggingAPastCompletedInterview() {
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenReturn(application);
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.create(USER_ID, 5L, request(YESTERDAY, InterviewStatus.COMPLETED)).status())
                .isEqualTo(InterviewStatus.COMPLETED);
    }

    @Test
    void rejectsCompletedInterviewInTheFuture() {
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenReturn(application);
        assertThatThrownBy(() -> service.create(USER_ID, 5L, request(TOMORROW, InterviewStatus.COMPLETED)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsSchedulingForAClosedApplication() {
        application.setStatus(ApplicationStatus.WITHDRAWN);
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenReturn(application);
        assertThatThrownBy(() -> service.create(USER_ID, 5L, request(TOMORROW, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotAddInterviewToAnotherUsersApplication() {
        when(applicationService.getOwnedApplication(USER_ID, 5L)).thenThrow(new ResourceNotFoundException("Application", 5L));
        assertThatThrownBy(() -> service.create(USER_ID, 5L, request(TOMORROW, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(interviewRepository, never()).save(any());
    }

    @Test
    void notesCanBeEditedAfterAScheduledInterviewHasPassed() {
        Interview interview = TestEntities.withId(
                new Interview(application, InterviewType.TECHNICAL, YESTERDAY, InterviewStatus.SCHEDULED), 7L);
        when(interviewRepository.findByIdAndApplicationUserId(7L, USER_ID)).thenReturn(Optional.of(interview));

        InterviewResponse response = service.update(USER_ID, 7L, new InterviewRequest(
                InterviewType.TECHNICAL, YESTERDAY, 60, "Grace", null, "Went well", InterviewStatus.SCHEDULED));

        assertThat(response.notes()).isEqualTo("Went well");
    }

    @Test
    void reschedulingIntoThePastIsRejected() {
        Interview interview = TestEntities.withId(
                new Interview(application, InterviewType.TECHNICAL, TOMORROW, InterviewStatus.SCHEDULED), 7L);
        when(interviewRepository.findByIdAndApplicationUserId(7L, USER_ID)).thenReturn(Optional.of(interview));

        assertThatThrownBy(() -> service.update(USER_ID, 7L, request(YESTERDAY, InterviewStatus.RESCHEDULED)))
                .isInstanceOf(BadRequestException.class);
        assertThat(interview.getScheduledAt()).isEqualTo(TOMORROW);
    }

    @Test
    void upcomingScopeOnlyReturnsPendingFutureInterviews() {
        Interview upcoming = TestEntities.withId(new Interview(application, InterviewType.FINAL, TOMORROW, InterviewStatus.SCHEDULED), 1L);
        Interview cancelled = TestEntities.withId(new Interview(application, InterviewType.FINAL, TOMORROW, InterviewStatus.CANCELLED), 2L);
        Interview past = TestEntities.withId(new Interview(application, InterviewType.FINAL, YESTERDAY, InterviewStatus.COMPLETED), 3L);
        when(interviewRepository.findByApplicationUserIdOrderByScheduledAtAsc(USER_ID))
                .thenReturn(List.of(past, upcoming, cancelled));

        assertThat(service.listForUser(USER_ID, InterviewService.Scope.UPCOMING))
                .extracting(InterviewResponse::id).containsExactly(1L);
        assertThat(service.listForUser(USER_ID, InterviewService.Scope.PAST))
                .extracting(InterviewResponse::id).containsExactly(2L, 3L);
    }

    private static InterviewRequest request(Instant scheduledAt, InterviewStatus status) {
        return new InterviewRequest(InterviewType.TECHNICAL, scheduledAt, 60, " Grace ", null, null, status);
    }
}
