package com.jobtrack.service;

import com.jobtrack.dto.dashboard.DashboardResponse;
import com.jobtrack.dto.dashboard.DashboardResponse.MonthCountResponse;
import com.jobtrack.dto.dashboard.DashboardResponse.StatusCountResponse;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.InterviewStatus;
import com.jobtrack.mapper.InterviewMapper;
import com.jobtrack.mapper.TaskMapper;
import com.jobtrack.repository.ApplicationStatusHistoryRepository;
import com.jobtrack.repository.FollowUpTaskRepository;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusCount;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates the dashboard from the database. Each figure is a COUNT/GROUP BY query rather than
 * loading every application into memory.
 */
@Service
public class DashboardService {

    static final int MONTHS_OF_HISTORY = 6;
    static final int UPCOMING_LIMIT = 5;
    private static final Set<InterviewStatus> PENDING_INTERVIEW = EnumSet.of(
            InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED);

    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final InterviewRepository interviewRepository;
    private final FollowUpTaskRepository taskRepository;
    private final Clock clock;

    public DashboardService(JobApplicationRepository applicationRepository,
                            ApplicationStatusHistoryRepository historyRepository,
                            InterviewRepository interviewRepository,
                            FollowUpTaskRepository taskRepository,
                            Clock clock) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.interviewRepository = interviewRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    /**
     * @param zone the user's time zone, used to decide what "today" and "this month" mean
     */
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, ZoneId zone) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        YearMonth thisMonth = YearMonth.from(today);

        List<StatusCountResponse> breakdown = statusBreakdown(userId);
        long total = breakdown.stream().mapToLong(StatusCountResponse::count).sum();

        return new DashboardResponse(
                total,
                applicationRepository.countByUserIdAndApplicationDateBetween(
                        userId, thisMonth.atDay(1), thisMonth.atEndOfMonth()),
                applicationRepository.countByUserIdAndStatusIn(userId, ApplicationStatus.ACTIVE),
                interviewRepository.countByApplicationUserId(userId),
                interviewRepository.countByApplicationUserIdAndStatusInAndScheduledAtAfter(
                        userId, PENDING_INTERVIEW, now),
                historyRepository.countApplicationsThatReachedStatus(userId, ApplicationStatus.OFFER),
                countFor(breakdown, ApplicationStatus.REJECTED),
                taskRepository.countByApplicationUserIdAndCompletedFalse(userId),
                taskRepository.countByApplicationUserIdAndCompletedFalseAndDueDateBefore(userId, today),
                breakdown,
                applicationsPerMonth(userId, thisMonth),
                interviewRepository.findByApplicationUserIdAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
                                userId, PENDING_INTERVIEW, now, PageRequest.of(0, UPCOMING_LIMIT)).stream()
                        .map(InterviewMapper::toResponse).toList(),
                taskRepository.findForUserByCompleted(userId, false, PageRequest.of(0, UPCOMING_LIMIT)).stream()
                        .map(TaskMapper::toResponse).toList());
    }

    /** One entry per status in pipeline order, including statuses with no applications. */
    private List<StatusCountResponse> statusBreakdown(Long userId) {
        Map<ApplicationStatus, Long> counts = applicationRepository.countByStatusForUser(userId).stream()
                .collect(Collectors.toMap(StatusCount::status, StatusCount::count));
        return Arrays.stream(ApplicationStatus.values())
                .map(status -> new StatusCountResponse(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    /** The last {@value #MONTHS_OF_HISTORY} months including the current one, zero-filled. */
    private List<MonthCountResponse> applicationsPerMonth(Long userId, YearMonth thisMonth) {
        YearMonth first = thisMonth.minusMonths(MONTHS_OF_HISTORY - 1);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : applicationRepository.countPerMonthSince(userId, first.atDay(1))) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        List<MonthCountResponse> months = new ArrayList<>(MONTHS_OF_HISTORY);
        for (YearMonth month = first; !month.isAfter(thisMonth); month = month.plusMonths(1)) {
            String key = month.toString(); // yyyy-MM, same format as the query
            months.add(new MonthCountResponse(key, counts.getOrDefault(key, 0L)));
        }
        return months;
    }

    private static long countFor(List<StatusCountResponse> breakdown, ApplicationStatus status) {
        return breakdown.stream().filter(s -> s.status() == status).mapToLong(StatusCountResponse::count)
                .findFirst().orElse(0);
    }
}
