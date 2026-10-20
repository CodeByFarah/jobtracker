package com.jobtrack.logo;

import com.jobtrack.entity.Company;
import com.jobtrack.entity.CompanyLogo;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.repository.CompanyLogoRepository;
import com.jobtrack.repository.CompanyRepository;
import com.jobtrack.repository.EmailEventRepository;
import com.jobtrack.repository.JobApplicationRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Looks up company logos in the background, one company at a time, so creating a company or an
 * application never waits on someone else's website. Companies are looked up when they're created,
 * when their name or website changes, when they get their first job link, and once at startup for
 * any company never checked.
 */
@Service
public class CompanyLogoService {

    private static final Logger log = LoggerFactory.getLogger(CompanyLogoService.class);

    private final CompanyRepository companyRepository;
    private final CompanyLogoRepository logoRepository;
    private final JobApplicationRepository applicationRepository;
    private final EmailEventRepository eventRepository;
    private final CompanyLogoFinder finder;
    private final Clock clock;
    private final boolean enabled;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "company-logos");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();

    public CompanyLogoService(CompanyRepository companyRepository, CompanyLogoRepository logoRepository,
                              JobApplicationRepository applicationRepository, EmailEventRepository eventRepository,
                              CompanyLogoFinder finder, Clock clock,
                              @Value("${jobtrack.logos.enabled:true}") boolean enabled) {
        this.companyRepository = companyRepository;
        this.logoRepository = logoRepository;
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.finder = finder;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** Companies that were never looked up (including everyone's companies from before logos existed). */
    @EventListener(ApplicationReadyEvent.class)
    void backfill() {
        if (enabled) {
            companyRepository.findIdsWithoutLogoCheck().forEach(this::lookUp);
        }
    }

    /**
     * Queues a lookup. Inside a transaction it starts after the commit, so the worker sees the new
     * company (or application) in the database.
     */
    public void lookUp(Long companyId) {
        if (!enabled || companyId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(companyId);
                }
            });
        } else {
            submit(companyId);
        }
    }

    /** A company without a logo yet: worth another look now that there's more to go on. */
    public void lookUpIfMissing(Company company) {
        if (company != null && company.getLogoUpdatedAt() == null) {
            lookUp(company.getId());
        }
    }

    public Optional<CompanyLogo> logo(Long companyId) {
        return logoRepository.findById(companyId);
    }

    private void submit(Long companyId) {
        if (queued.add(companyId)) {
            worker.submit(() -> {
                queued.remove(companyId);
                try {
                    refresh(companyId);
                } catch (RuntimeException e) {
                    log.warn("Logo lookup for company {} failed: {}", companyId, e.getMessage());
                }
            });
        }
    }

    void refresh(Long companyId) {
        Optional<Company> found = companyRepository.findById(companyId);
        if (found.isEmpty()) {
            return;
        }
        Company company = found.get();
        Optional<CompanyLogoFinder.FoundLogo> logo = finder.find(company.getName(), hints(company));
        Instant now = clock.instant();
        if (logo.isEmpty()) {
            companyRepository.markLogoChecked(companyId, now);
            log.debug("No logo found for company {} ({})", companyId, company.getName());
            return;
        }
        CompanyLogo stored = logoRepository.findById(companyId).orElseGet(() -> new CompanyLogo(companyId));
        stored.replace(logo.get().contentType(), logo.get().image(), logo.get().sourceUrl(), now);
        logoRepository.save(stored);
        companyRepository.markLogoUpdated(companyId, now);
        companyRepository.fillWebsiteIfEmpty(companyId, "https://" + logo.get().domain());
        log.info("Logo for {} from {}", company.getName(), logo.get().sourceUrl());
    }

    /** Where the company's website might be written down: its website field, job links, email senders. */
    private List<String> hints(Company company) {
        List<String> hints = new ArrayList<>();
        if (company.getWebsite() != null) {
            hints.add(company.getWebsite());
        }
        for (JobApplication application : applicationRepository.findByCompanyIdOrderByUpdatedAtDesc(company.getId())) {
            if (application.getJobUrl() != null) {
                hints.add(application.getJobUrl());
            }
        }
        hints.addAll(eventRepository.findSendersByCompanyId(company.getId()));
        return hints;
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
