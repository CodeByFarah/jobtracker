package com.jobtrack.gmail;

import com.jobtrack.dto.gmail.ApplicationUpdate;
import com.jobtrack.dto.gmail.EmailEventResponse;
import com.jobtrack.dto.gmail.StatusChange;
import com.jobtrack.dto.gmail.SyncSummary;
import com.jobtrack.dto.gmail.UpdateReport;
import com.jobtrack.entity.EmailEvent;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.EmailEventRepository;
import com.jobtrack.repository.JobApplicationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Read side of the email events: what the AI read and what it changed. */
@Service
public class EmailEventService {

    private final EmailEventRepository eventRepository;
    private final JobApplicationRepository applicationRepository;
    private final Clock clock;

    public EmailEventService(EmailEventRepository eventRepository, JobApplicationRepository applicationRepository,
                             Clock clock) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EmailEventResponse> recent(Long userId, int limit) {
        return eventRepository.findByUserIdAndApplicationIsNotNullOrderByReceivedAtDesc(userId, PageRequest.of(0, limit))
                .stream().map(EmailEventService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EmailEventResponse> forApplication(Long userId, Long applicationId) {
        applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        return eventRepository.findByUserIdAndApplicationIdOrderByReceivedAtDesc(userId, applicationId)
                .stream().map(EmailEventService::toResponse).toList();
    }

    /** Every application with its current status and latest matched email, plus the last check's summary. */
    @Transactional(readOnly = true)
    public UpdateReport report(Long userId, SyncSummary lastCheck) {
        Map<Long, EmailEvent> latestEmail = new HashMap<>();
        for (EmailEvent event : eventRepository.findByUserIdAndApplicationIsNotNullOrderByReceivedAtDesc(
                userId, PageRequest.of(0, 1000))) {
            latestEmail.putIfAbsent(event.getApplication().getId(), event);
        }
        Set<Long> changed = lastCheck == null || lastCheck.changes() == null ? Set.of()
                : lastCheck.changes().stream().map(StatusChange::applicationId).collect(Collectors.toSet());
        Instant now = clock.instant();

        List<ApplicationUpdate> applications = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(application -> {
                    EmailEvent email = latestEmail.get(application.getId());
                    return new ApplicationUpdate(
                            application.getId(),
                            application.getJobTitle(),
                            application.getCompany().getId(),
                            application.getCompany().getName(),
                            application.getCompany().getLogoUpdatedAt(),
                            application.getStatus(),
                            application.getApplicationDate(),
                            application.getUpdatedAt(),
                            email == null ? null : email.getReceivedAt(),
                            email == null ? null : email.getCategory(),
                            email == null ? null : email.getSummary(),
                            changed.contains(application.getId()),
                            NoReplyRule.applies(application, now));
                })
                // Changed in the last check first, then still-open applications, then closed ones.
                .sorted(Comparator.comparing((ApplicationUpdate u) -> !u.changedInLastCheck())
                        .thenComparing(u -> u.status().isClosed()))
                .toList();
        return new UpdateReport(lastCheck, applications);
    }

    static EmailEventResponse toResponse(EmailEvent event) {
        JobApplication application = event.getApplication();
        return new EmailEventResponse(
                event.getId(),
                application == null ? null : application.getId(),
                application == null ? null : application.getJobTitle(),
                application == null ? null : application.getCompany().getName(),
                event.getSender(),
                event.getSubject(),
                event.getReceivedAt(),
                event.getCategory(),
                event.getConfidence(),
                event.getSummary(),
                event.getStatusBefore(),
                event.getStatusAfter());
    }
}
