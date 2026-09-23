package com.jobtrack.service;

import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.entity.Interview;
import com.jobtrack.entity.InterviewStatus;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.mapper.InterviewMapper;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

@Service
public class InterviewService {

    public enum Scope { ALL, UPCOMING, PAST }

    private final InterviewRepository interviewRepository;
    private final ApplicationService applicationService;
    private final Clock clock;

    public InterviewService(InterviewRepository interviewRepository, ApplicationService applicationService,
                            Clock clock) {
        this.interviewRepository = interviewRepository;
        this.applicationService = applicationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> listForApplication(Long userId, Long applicationId) {
        applicationService.getOwnedApplication(userId, applicationId);
        return interviewRepository.findByApplicationIdOrderByScheduledAtAsc(applicationId).stream()
                .map(InterviewMapper::toResponse)
                .toList();
    }

    /**
     * All of the user's interviews across applications. "Upcoming" means still pending
     * (scheduled/rescheduled) and in the future; everything else is "past".
     */
    @Transactional(readOnly = true)
    public List<InterviewResponse> listForUser(Long userId, Scope scope) {
        Instant now = clock.instant();
        Predicate<Interview> upcoming = i -> i.getStatus().isPending() && i.getScheduledAt().isAfter(now);
        Predicate<Interview> filter = switch (scope) {
            case ALL -> i -> true;
            case UPCOMING -> upcoming;
            case PAST -> upcoming.negate();
        };
        List<Interview> interviews = interviewRepository.findByApplicationUserIdOrderByScheduledAtAsc(userId)
                .stream().filter(filter).toList();
        // Past interviews read best most-recent-first.
        if (scope == Scope.PAST) {
            interviews = interviews.reversed();
        }
        return interviews.stream().map(InterviewMapper::toResponse).toList();
    }

    @Transactional
    public InterviewResponse create(Long userId, Long applicationId, InterviewRequest request) {
        JobApplication application = applicationService.getOwnedApplication(userId, applicationId);
        InterviewStatus status = request.status() != null ? request.status() : InterviewStatus.SCHEDULED;
        validate(application, status, request.scheduledAt(), true);

        Interview interview = new Interview(application, request.type(), request.scheduledAt(), status);
        applyDetails(interview, request);
        return InterviewMapper.toResponse(interviewRepository.save(interview));
    }

    @Transactional
    public InterviewResponse update(Long userId, Long interviewId, InterviewRequest request) {
        Interview interview = getOwnedInterview(userId, interviewId);
        InterviewStatus status = request.status() != null ? request.status() : interview.getStatus();
        // Only re-check the schedule when it changes, so notes can still be edited on an
        // interview whose time has passed but which hasn't been marked completed yet.
        boolean scheduleChanged = !request.scheduledAt().equals(interview.getScheduledAt())
                || status != interview.getStatus();
        validate(interview.getApplication(), status, request.scheduledAt(), scheduleChanged);

        interview.setType(request.type());
        interview.setScheduledAt(request.scheduledAt());
        interview.setStatus(status);
        applyDetails(interview, request);
        interviewRepository.flush();
        return InterviewMapper.toResponse(interview);
    }

    @Transactional
    public void delete(Long userId, Long interviewId) {
        interviewRepository.delete(getOwnedInterview(userId, interviewId));
    }

    /**
     * Rejects interview records that cannot be right:
     * <ul>
     *   <li>a pending (scheduled/rescheduled) interview in the past;</li>
     *   <li>a completed interview in the future;</li>
     *   <li>scheduling a new interview for an application that is already closed.</li>
     * </ul>
     * A missing application is impossible by construction: interviews are only created under an
     * application the user owns, and the foreign key is NOT NULL.
     */
    private void validate(JobApplication application, InterviewStatus status, Instant scheduledAt,
                          boolean scheduleChanged) {
        Instant now = clock.instant();
        if (scheduleChanged && status.isPending()) {
            if (application.getStatus().isClosed()) {
                throw new ConflictException("Cannot schedule an interview for an application that is "
                        + application.getStatus());
            }
            if (!scheduledAt.isAfter(now)) {
                throw new BadRequestException("scheduledAt",
                        "A scheduled interview must be in the future. Use status COMPLETED or CANCELLED to log a past interview.");
            }
        }
        if (status == InterviewStatus.COMPLETED && scheduledAt.isAfter(now)) {
            throw new BadRequestException("scheduledAt", "An interview in the future cannot be marked as completed");
        }
    }

    private Interview getOwnedInterview(Long userId, Long interviewId) {
        return interviewRepository.findByIdAndApplicationUserId(interviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview", interviewId));
    }

    private static void applyDetails(Interview interview, InterviewRequest request) {
        interview.setDurationMinutes(request.durationMinutes());
        interview.setInterviewerName(TextUtils.clean(request.interviewerName()));
        interview.setLocation(TextUtils.clean(request.location()));
        interview.setNotes(TextUtils.clean(request.notes()));
    }
}
