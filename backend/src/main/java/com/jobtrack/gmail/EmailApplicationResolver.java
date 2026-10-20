package com.jobtrack.gmail;

import com.jobtrack.dto.application.CreateApplicationRequest;
import com.jobtrack.dto.company.CompanyRequest;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.EmailCategory;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.repository.CompanyRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.service.ApplicationService;
import com.jobtrack.service.CompanyService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * For an email the AI says is about one of the user's applications but that matched nothing they
 * track: finds their application at that company by name, or adds it, so every application email
 * ends up on an application even if the user never typed it in.
 */
@Component
public class EmailApplicationResolver {

    /** Adding an application is a bigger step than moving one, so it needs a surer reading. */
    static final double MIN_CONFIDENCE_TO_CREATE = 0.7;
    static final String UNKNOWN_TITLE = "Position not stated";

    /** Email kinds that prove the person applied somewhere. */
    private static final Set<EmailCategory> APPLICATION_EMAILS = EnumSet.of(EmailCategory.APPLICATION_RECEIVED,
            EmailCategory.ASSESSMENT, EmailCategory.INTERVIEW, EmailCategory.OFFER, EmailCategory.REJECTED);
    private static final Set<ApplicationStatus> OPEN = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
            ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    /** Names a small model sometimes gives instead of the employer. */
    private static final Set<String> NOT_EMPLOYERS = Set.of("linkedin", "indeed", "glassdoor", "greenhouse", "lever",
            "workday", "smartrecruiters", "ashby", "workable", "jobvite", "icims", "taleo", "teamtailor", "kariyer",
            "kariyer net", "kariyernet", "secretcv", "yenibiris", "gmail", "google careers team", "company",
            "the company", "hiring team", "recruiting team", "talent team", "hr", "unknown");

    public record Resolved(JobApplication application, boolean created) {
    }

    private final JobApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationService applicationService;
    private final CompanyService companyService;

    public EmailApplicationResolver(JobApplicationRepository applicationRepository, CompanyRepository companyRepository,
                                    ApplicationService applicationService, CompanyService companyService) {
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.applicationService = applicationService;
        this.companyService = companyService;
    }

    /** The application this email belongs to (found or newly added), or empty if it can't tell. */
    public Optional<Resolved> resolve(Long userId, GmailMessage message, EmailClassification reading) {
        if (!APPLICATION_EMAILS.contains(reading.category()) || reading.confidence() < MIN_CONFIDENCE_TO_CREATE
                || reading.companyName() == null) {
            return Optional.empty();
        }
        String company = CompanyMatcher.coreName(reading.companyName());
        if (company.length() < 2 || NOT_EMPLOYERS.contains(company)) {
            return Optional.empty();
        }

        List<JobApplication> atCompany = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(a -> CompanyMatcher.coreName(a.getCompany().getName()).equals(company))
                .toList();
        Optional<JobApplication> existing = pick(atCompany, reading.jobTitle());
        if (existing.isPresent()) {
            return Optional.of(new Resolved(existing.get(), false));
        }
        return Optional.of(new Resolved(create(userId, message, reading), true));
    }

    /**
     * Which of the user's applications at this company the email means. Without a job title, the most
     * recent open one; with one, an application with a similar title (otherwise it's a new application).
     */
    static Optional<JobApplication> pick(List<JobApplication> atCompany, String jobTitle) {
        if (atCompany.isEmpty()) {
            return Optional.empty();
        }
        String title = jobTitle == null ? "" : CompanyMatcher.normalize(jobTitle);
        if (title.isEmpty()) {
            return atCompany.stream().filter(a -> OPEN.contains(a.getStatus())).findFirst()
                    .or(() -> Optional.of(atCompany.get(0)));
        }
        Optional<JobApplication> sameTitle = atCompany.stream()
                .filter(a -> similarTitles(CompanyMatcher.normalize(a.getJobTitle()), title))
                .findFirst();
        if (sameTitle.isPresent()) {
            return sameTitle;
        }
        // An application added earlier from an email that didn't name the job is this one.
        return atCompany.stream().filter(a -> UNKNOWN_TITLE.equals(a.getJobTitle())).findFirst();
    }

    private static boolean similarTitles(String a, String b) {
        return !a.isEmpty() && (a.equals(b) || a.contains(b) || b.contains(a));
    }

    private JobApplication create(Long userId, GmailMessage message, EmailClassification reading) {
        String companyName = reading.companyName().strip();
        Company company = companyRepository.findFirstByUserIdAndNameIgnoreCase(userId, companyName).orElse(null);
        Long companyId = company != null ? company.getId() : companyService.create(userId,
                new CompanyRequest(companyName, null, null, null, "Added automatically from your email.")).id();

        String title = reading.jobTitle() == null ? UNKNOWN_TITLE : reading.jobTitle();
        LocalDate applied = LocalDate.ofInstant(message.receivedAt(), ZoneOffset.UTC);
        String notes = "Added automatically from an email received " + applied + ": \""
                + (message.subject() == null ? "(no subject)" : message.subject()) + "\"";
        Long id = applicationService.create(userId, new CreateApplicationRequest(companyId, title, null, null, null,
                null, null, null, applied, ApplicationStatus.APPLIED, notes)).id();
        // Loaded with its company (this runs outside a web request, so lazy loading isn't available).
        return applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
