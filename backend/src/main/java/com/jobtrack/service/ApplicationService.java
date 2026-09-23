package com.jobtrack.service;

import com.jobtrack.dto.application.ApplicationFields;
import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.ApplicationSearchCriteria;
import com.jobtrack.dto.application.CreateApplicationRequest;
import com.jobtrack.dto.application.StatusHistoryResponse;
import com.jobtrack.dto.application.UpdateApplicationRequest;
import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.ApplicationStatusHistory;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.mapper.ApplicationMapper;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.repository.ApplicationSpecifications;
import com.jobtrack.repository.ApplicationStatusHistoryRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final Clock clock;

    public ApplicationService(JobApplicationRepository applicationRepository,
                              ApplicationStatusHistoryRepository historyRepository,
                              CompanyService companyService,
                              UserRepository userRepository,
                              Clock clock) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.companyService = companyService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Filtering, sorting and pagination all happen in the database query. */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> search(Long userId, ApplicationSearchCriteria criteria, Pageable pageable) {
        ApplicationSearchCriteria cleaned = new ApplicationSearchCriteria(
                TextUtils.clean(criteria.query()),
                criteria.status(),
                TextUtils.clean(criteria.location()),
                criteria.employmentType(),
                criteria.companyId());
        return PageResponse.from(
                applicationRepository.findAll(ApplicationSpecifications.forUser(userId, cleaned), pageable),
                ApplicationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long userId, Long applicationId) {
        return ApplicationMapper.toResponse(getOwnedApplication(userId, applicationId));
    }

    /**
     * Creates the application and its first status-history entry (previous status = null) in one
     * transaction, so every application's timeline starts at the status it was created with.
     */
    @Transactional
    public ApplicationResponse create(Long userId, CreateApplicationRequest request) {
        Company company = companyService.getOwnedCompany(userId, request.companyId());
        ApplicationStatus status = request.status() != null ? request.status() : ApplicationStatus.SAVED;

        JobApplication application = new JobApplication(
                userRepository.getReferenceById(userId), company, TextUtils.clean(request.jobTitle()), status);
        applyFields(application, request);
        if (application.getApplicationDate() == null && status != ApplicationStatus.SAVED) {
            application.setApplicationDate(today());
        }

        applicationRepository.save(application);
        recordStatusChange(application, null, status);
        return ApplicationMapper.toResponse(application);
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long applicationId, UpdateApplicationRequest request) {
        JobApplication application = getOwnedApplication(userId, applicationId);
        if (!application.getCompany().getId().equals(request.companyId())) {
            application.setCompany(companyService.getOwnedCompany(userId, request.companyId()));
        }
        application.setJobTitle(TextUtils.clean(request.jobTitle()));
        applyFields(application, request);
        applicationRepository.flush();
        return ApplicationMapper.toResponse(application);
    }

    /**
     * Moves the application to a new status and appends a history entry.
     * <p>
     * Both writes run in one transaction: if saving the history entry fails, the status update is
     * rolled back too, so the current status can never disagree with the latest history entry.
     */
    @Transactional
    public ApplicationResponse changeStatus(Long userId, Long applicationId, ApplicationStatus newStatus) {
        JobApplication application = getOwnedApplication(userId, applicationId);
        ApplicationStatus previousStatus = application.getStatus();

        if (previousStatus == newStatus) {
            throw new ConflictException("Application is already in status " + newStatus);
        }
        if (!previousStatus.canTransitionTo(newStatus)) {
            throw new ConflictException(transitionError(previousStatus, newStatus));
        }

        application.setStatus(newStatus);
        if (application.getApplicationDate() == null && newStatus != ApplicationStatus.SAVED) {
            application.setApplicationDate(today());
        }
        recordStatusChange(application, previousStatus, newStatus);
        applicationRepository.flush();
        return ApplicationMapper.toResponse(application);
    }

    /** Deletes the application; its history, interviews and tasks are removed by ON DELETE CASCADE. */
    @Transactional
    public void delete(Long userId, Long applicationId) {
        applicationRepository.delete(getOwnedApplication(userId, applicationId));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getHistory(Long userId, Long applicationId) {
        getOwnedApplication(userId, applicationId);
        return historyRepository.findByApplicationIdOrderByChangedAtAscIdAsc(applicationId).stream()
                .map(ApplicationMapper::toResponse)
                .toList();
    }

    /** Returns the application if it belongs to the user; otherwise 404 (not 403) to avoid leaking ids. */
    JobApplication getOwnedApplication(Long userId, Long applicationId) {
        return applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
    }

    private void recordStatusChange(JobApplication application, ApplicationStatus from, ApplicationStatus to) {
        historyRepository.save(new ApplicationStatusHistory(application, from, to, clock.instant()));
    }

    private void applyFields(JobApplication application, ApplicationFields fields) {
        validateFields(fields);
        application.setJobUrl(TextUtils.clean(fields.jobUrl()));
        application.setLocation(TextUtils.clean(fields.location()));
        application.setEmploymentType(fields.employmentType());
        application.setSalaryMin(fields.salaryMin());
        application.setSalaryMax(fields.salaryMax());
        String currency = TextUtils.clean(fields.salaryCurrency());
        application.setSalaryCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
        application.setApplicationDate(fields.applicationDate());
        application.setNotes(TextUtils.clean(fields.notes()));
    }

    /** Rules that involve more than one field or the current date. */
    private void validateFields(ApplicationFields fields) {
        if (fields.salaryMin() != null && fields.salaryMax() != null && fields.salaryMin() > fields.salaryMax()) {
            throw new BadRequestException("salaryMax", "salaryMax must be greater than or equal to salaryMin");
        }
        // One day of tolerance: the server runs in UTC, while users may already be "tomorrow".
        if (fields.applicationDate() != null && fields.applicationDate().isAfter(today().plusDays(1))) {
            throw new BadRequestException("applicationDate", "applicationDate cannot be in the future");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String transitionError(ApplicationStatus from, ApplicationStatus to) {
        if (from.isClosed()) {
            return "Application is " + from + ", which is a final status; it cannot move to " + to;
        }
        String allowed = from.allowedTransitions().stream().map(Enum::name).collect(Collectors.joining(", "));
        return "Cannot change status from " + from + " to " + to + ". Allowed next statuses: " + allowed;
    }
}
