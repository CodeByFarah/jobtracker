package com.jobtrack.service;

import com.jobtrack.dto.dashboard.DashboardResponse;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.repository.ApplicationStatusHistoryRepository;
import com.jobtrack.repository.FollowUpTaskRepository;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final long USER_ID = 1L;
    // 23:30 UTC on 30 Sep = already 1 October in Tokyo
    private static final Instant NOW = Instant.parse("2026-09-30T23:30:00Z");

    @Mock
    JobApplicationRepository applicationRepository;
    @Mock
    ApplicationStatusHistoryRepository historyRepository;
    @Mock
    InterviewRepository interviewRepository;
    @Mock
    FollowUpTaskRepository taskRepository;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(applicationRepository, historyRepository, interviewRepository, taskRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(applicationRepository.countByStatusForUser(USER_ID)).thenReturn(List.of(
                new StatusCount(ApplicationStatus.APPLIED, 12),
                new StatusCount(ApplicationStatus.SCREENING, 4),
                new StatusCount(ApplicationStatus.INTERVIEW, 3),
                new StatusCount(ApplicationStatus.OFFER, 1),
                new StatusCount(ApplicationStatus.REJECTED, 8)));
        when(applicationRepository.countPerMonthSince(eq(USER_ID), any())).thenReturn(rows(
                new Object[]{"2026-05", 3L}, new Object[]{"2026-09", 7L}));
        when(interviewRepository.findByApplicationUserIdAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
                anyLong(), any(), any(), any())).thenReturn(List.of());
        when(taskRepository.findForUserByCompleted(anyLong(), anyBoolean(), any())).thenReturn(List.of());
    }

    @Test
    void totalsAndBreakdownComeFromTheGroupedCounts() {
        DashboardResponse dashboard = service.getDashboard(USER_ID, ZoneOffset.UTC);

        assertThat(dashboard.totalApplications()).isEqualTo(28);
        assertThat(dashboard.rejected()).isEqualTo(8);
        assertThat(dashboard.statusBreakdown())
                .extracting(DashboardResponse.StatusCountResponse::status)
                .containsExactly(ApplicationStatus.values());
        assertThat(dashboard.statusBreakdown())
                .extracting(DashboardResponse.StatusCountResponse::count)
                .containsExactly(0L, 12L, 4L, 3L, 1L, 0L, 8L, 0L);
    }

    @Test
    void passesThroughRepositoryCounts() {
        when(applicationRepository.countByUserIdAndStatusIn(USER_ID, ApplicationStatus.ACTIVE)).thenReturn(20L);
        when(historyRepository.countApplicationsThatReachedStatus(USER_ID, ApplicationStatus.OFFER)).thenReturn(2L);
        when(interviewRepository.countByApplicationUserId(USER_ID)).thenReturn(9L);
        when(taskRepository.countByApplicationUserIdAndCompletedFalse(USER_ID)).thenReturn(5L);

        DashboardResponse dashboard = service.getDashboard(USER_ID, ZoneOffset.UTC);

        assertThat(dashboard.activeApplications()).isEqualTo(20);
        assertThat(dashboard.offers()).isEqualTo(2);
        assertThat(dashboard.totalInterviews()).isEqualTo(9);
        assertThat(dashboard.outstandingTasks()).isEqualTo(5);
    }

    @Test
    void monthlySeriesCoversSixMonthsAndFillsGapsWithZero() {
        DashboardResponse dashboard = service.getDashboard(USER_ID, ZoneOffset.UTC);

        assertThat(dashboard.applicationsPerMonth())
                .extracting(DashboardResponse.MonthCountResponse::month)
                .containsExactly("2026-04", "2026-05", "2026-06", "2026-07", "2026-08", "2026-09");
        assertThat(dashboard.applicationsPerMonth())
                .extracting(DashboardResponse.MonthCountResponse::count)
                .containsExactly(0L, 3L, 0L, 0L, 0L, 7L);
        verify(applicationRepository).countPerMonthSince(USER_ID, LocalDate.of(2026, 4, 1));
    }

    @Test
    void thisMonthAndOverdueUseTheCallersTimeZone() {
        service.getDashboard(USER_ID, ZoneId.of("Asia/Tokyo"));

        // In Tokyo it is already 1 October 2026
        verify(applicationRepository).countByUserIdAndApplicationDateBetween(
                USER_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        verify(taskRepository).countByApplicationUserIdAndCompletedFalseAndDueDateBefore(
                USER_ID, LocalDate.of(2026, 10, 1));
    }

    private static List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }
}
