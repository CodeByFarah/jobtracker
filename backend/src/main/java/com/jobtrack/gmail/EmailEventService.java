package com.jobtrack.gmail;

import com.jobtrack.dto.gmail.EmailEventResponse;
import com.jobtrack.entity.EmailEvent;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.EmailEventRepository;
import com.jobtrack.repository.JobApplicationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side of the email events: what the AI read and what it changed. */
@Service
public class EmailEventService {

    private final EmailEventRepository eventRepository;
    private final JobApplicationRepository applicationRepository;

    public EmailEventService(EmailEventRepository eventRepository, JobApplicationRepository applicationRepository) {
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
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
