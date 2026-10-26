package com.jobtrack.gmail;

import com.jobtrack.config.GmailProperties;
import com.jobtrack.dto.gmail.StatusChange;
import com.jobtrack.dto.gmail.SyncSummary;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;
import com.jobtrack.entity.EmailEvent;
import com.jobtrack.entity.GmailConnection;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.AiUnavailableException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.GmailAuthException;
import com.jobtrack.logo.CompanyLogoService;
import com.jobtrack.repository.EmailEventRepository;
import com.jobtrack.repository.GmailConnectionRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.ApplicationService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads new Gmail messages, lets the AI decide what each one means, and moves the matching
 * applications forward automatically. Every email the AI read is stored as an {@link EmailEvent},
 * so the user can see why a status changed and nothing is processed twice.
 * <p>
 * Syncs run in the background on a small worker pool (GMAIL_SYNC_WORKERS, default 1: a local
 * model handles one request at a time anyway). The sync state (a lease, progress and the last
 * report) is kept on the mailbox's row in the database, so any number of backend instances can
 * run together without checking the same mailbox twice, and each can show any user's progress.
 */
@Service
public class EmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(EmailSyncService.class);

    /** Below this the AI's reading is still recorded, but the status is left alone. */
    static final double MIN_CONFIDENCE_TO_UPDATE = 0.6;
    private static final Set<ApplicationStatus> OPEN = EnumSet.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
            ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    /**
     * Skip mail that is never about an application. The Promotions tab is skipped (courses, offers,
     * job alerts); the Social tab is searched because LinkedIn application updates can land there.
     */
    private static final String BASE_QUERY =
            "-in:sent -in:drafts -in:chats -in:spam -in:trash -category:promotions";
    /** How long a check may go without reporting progress before another instance may take over. */
    static final Duration LEASE = Duration.ofMinutes(10);

    private final GmailConnectionRepository connectionRepository;
    private final EmailEventRepository eventRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationService applicationService;
    private final GmailConnectionService connectionService;
    private final GmailApiClient gmail;
    private final EmailClassifier classifier;
    private final UpdateSummaryWriter summaryWriter;
    private final EmailApplicationResolver resolver;
    private final CompanyLogoService logoService;
    private final GmailProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    private final ExecutorService worker;
    /** Users queued or running on this instance, so a double click doesn't queue a second check. */
    private final Set<Long> queuedOrRunning = ConcurrentHashMap.newKeySet();

    public EmailSyncService(GmailConnectionRepository connectionRepository, EmailEventRepository eventRepository,
                            JobApplicationRepository applicationRepository, UserRepository userRepository,
                            ApplicationService applicationService, GmailConnectionService connectionService,
                            GmailApiClient gmail, EmailClassifier classifier, UpdateSummaryWriter summaryWriter,
                            EmailApplicationResolver resolver, CompanyLogoService logoService,
                            GmailProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.connectionRepository = connectionRepository;
        this.eventRepository = eventRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.applicationService = applicationService;
        this.connectionService = connectionService;
        this.gmail = gmail;
        this.classifier = classifier;
        this.summaryWriter = summaryWriter;
        this.resolver = resolver;
        this.logoService = logoService;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        AtomicInteger threads = new AtomicInteger();
        this.worker = Executors.newFixedThreadPool(properties.syncWorkers(), runnable -> {
            Thread thread = new Thread(runnable, "gmail-sync-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Queues a sync for the user. Returns false if one is already queued or running. */
    public boolean requestSync(Long userId) {
        if (!queuedOrRunning.add(userId)) {
            return false;
        }
        worker.submit(() -> {
            try {
                syncNow(userId);
            } catch (RuntimeException e) {
                log.error("Gmail sync for user {} failed unexpectedly", userId, e);
            } finally {
                queuedOrRunning.remove(userId);
            }
        });
        return true;
    }

    /** Where the user's check stands, as any instance sees it. */
    public State state(Long userId) {
        Optional<GmailConnectionRepository.SyncState> stored = connectionRepository.findSyncState(userId, clock.instant());
        boolean syncing = queuedOrRunning.contains(userId)
                || stored.map(s -> Boolean.TRUE.equals(s.getSyncing())).orElse(false);
        int done = stored.map(s -> s.getDone() == null ? 0 : s.getDone()).orElse(0);
        int total = stored.map(s -> s.getTotal() == null ? 0 : s.getTotal()).orElse(0);
        SyncSummary lastRun = stored.map(GmailConnectionRepository.SyncState::getReport).map(this::readReport).orElse(null);
        return new State(syncing, done, total, lastRun);
    }

    /** {@code done}/{@code total} are emails read so far and to read; both 0 when idle or still listing. */
    public record State(boolean syncing, int done, int total, SyncSummary lastRun) {
    }

    public Optional<SyncSummary> lastRun(Long userId) {
        return Optional.ofNullable(state(userId).lastRun());
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    /** Runs one sync in the calling thread. */
    void syncNow(Long userId) {
        Optional<GmailConnection> found = connectionRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return;
        }
        GmailConnection connection = found.get();
        Long connectionId = connection.getId();
        Instant startedAt = clock.instant();
        if (connectionRepository.claimSync(connectionId, startedAt, startedAt.plus(LEASE)) == 0) {
            log.info("Gmail sync for user {} skipped: a check is already running", userId);
            return;
        }
        int scanned = 0;
        int analyzed = 0;
        List<StatusChange> changes = new ArrayList<>();
        String error = null;
        String overview = null;
        boolean stillConnected = true;
        try {
            String accessToken = connectionService.accessToken(connection);
            List<String> trackedCompanies = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                    .map(a -> a.getCompany().getName())
                    .toList();
            List<String> ids = gmail.listMessageIds(accessToken,
                    query(connection.getLastSyncedAt(), trackedCompanies), properties.maxMessagesPerSync());
            List<GmailMessage> messages = new ArrayList<>();
            for (String id : ids) {
                if (!eventRepository.existsByUserIdAndGmailMessageId(userId, id)) {
                    messages.add(gmail.getMessage(accessToken, id));
                }
            }
            // Newest first, so the latest news shows up straight away even when a first check has
            // months of mail to read. Statuses only ever move forward (EmailStatusRules), so an older
            // email read afterwards can't undo a newer one.
            messages.sort(Comparator.comparing(GmailMessage::receivedAt).reversed());
            stillConnected = reportProgress(connectionId, 0, messages.size());

            for (GmailMessage message : messages) {
                if (!stillConnected) {
                    break; // disconnected or switched to another mailbox meanwhile: stop reading this one
                }
                scanned++;
                // Re-read every time: an earlier email in this run may have changed or closed an application.
                List<JobApplication> open = applicationRepository.findByUserIdAndStatusIn(userId, OPEN);
                List<JobApplication> candidates = CompanyMatcher.candidates(message, open);
                // Mail about a tracked company is always read; anything else that looks like hiring
                // mail is read too, so applications the user never added are found as well.
                if (!candidates.isEmpty() || JobEmailFilter.looksJobRelated(message)) {
                    analyzed++;
                    EmailClassification classification = classifier.classify(message, candidates);
                    StatusChange change = record(userId, message, classification, candidates);
                    if (change != null) {
                        changes.add(change);
                    }
                }
                stillConnected = reportProgress(connectionId, scanned, messages.size());
            }
            if (!stillConnected) {
                log.info("Gmail sync for user {} stopped: the mailbox was disconnected", userId);
                return;
            }
            overview = summaryWriter.write(applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId),
                    changes, analyzed, clock.instant());
        } catch (GmailAuthException | AiUnavailableException e) {
            error = e.getMessage();
        } catch (RestClientException e) {
            log.warn("Gmail sync for user {} failed: {}", userId, e.getMessage());
            error = "Couldn't reach Gmail: " + e.getMessage();
        } catch (RuntimeException e) {
            // Anything else must still end the check with a visible error, never a silent "no new emails".
            log.error("Gmail sync for user {} failed unexpectedly", userId, e);
            error = "The check stopped because of an unexpected error. Please try again.";
        }

        // Only move the "read up to" mark forward after a complete run, so a failed run is retried.
        Instant finishedAt = clock.instant();
        updateConnection(connectionId, error == null ? startedAt : null, error);
        SyncSummary summary = new SyncSummary(startedAt, finishedAt, scanned, analyzed, changes.size(), error,
                List.copyOf(changes), overview);
        connectionRepository.finishSync(connectionId, writeReport(summary));
        log.info("Gmail sync for user {}: {} new emails, {} read by AI, {} applications updated{}",
                userId, scanned, analyzed, changes.size(), error == null ? "" : " (stopped: " + error + ")");
    }

    /** Saves what the AI decided and applies the status change. Returns the change, or null if none. */
    private StatusChange record(Long userId, GmailMessage message, EmailClassification classification,
                                List<JobApplication> candidates) {
        JobApplication application = classification.applicationId() == null ? null : candidates.stream()
                .filter(a -> a.getId().equals(classification.applicationId()))
                .findFirst()
                .orElse(null);
        boolean created = false;
        if (application == null && classification.category() != EmailCategory.NOT_JOB_RELATED) {
            // About an application the pre-filter didn't link: find it by company name, or add it.
            Optional<EmailApplicationResolver.Resolved> resolved = resolver.resolve(userId, message, classification);
            if (resolved.isPresent()) {
                application = resolved.get().application();
                created = resolved.get().created();
            }
        }

        ApplicationStatus before = application == null || created ? null : application.getStatus();
        ApplicationStatus after = application == null ? null : application.getStatus();
        if (application != null && classification.confidence() >= MIN_CONFIDENCE_TO_UPDATE) {
            for (ApplicationStatus step : EmailStatusRules.steps(after, classification.category())) {
                try {
                    applicationService.changeStatus(userId, application.getId(), step, message.receivedAt());
                    after = step;
                } catch (ConflictException e) {
                    log.info("Skipped status change for application {}: {}", application.getId(), e.getMessage());
                    break;
                }
            }
        }

        EmailEvent event = new EmailEvent(userRepository.getReferenceById(userId), message.id(),
                message.receivedAt(), classification.category());
        if (application != null && classification.category() != EmailCategory.NOT_JOB_RELATED) {
            event.setApplication(applicationRepository.getReferenceById(application.getId()));
        }
        event.setSender(limit(message.from(), 320));
        event.setSubject(limit(message.subject(), 500));
        event.setConfidence(classification.confidence());
        event.setSummary(classification.summary());
        event.setStatusBefore(before);
        event.setStatusAfter(after == before ? null : after);
        try {
            eventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            log.debug("Email {} was already recorded", message.id());
        }
        if (application != null) {
            // Now that the email is saved, its sender's address can point the logo lookup at the
            // company's website.
            logoService.lookUpIfMissing(application.getCompany());
        }
        if (application == null || after == before) {
            return null;
        }
        return new StatusChange(application.getId(), application.getJobTitle(), application.getCompany().getName(),
                before, after, classification.summary());
    }

    /** Saves progress (which also keeps the lease). False if the mailbox is no longer connected. */
    private boolean reportProgress(Long connectionId, int done, int total) {
        return connectionRepository.recordProgress(connectionId, done, total, clock.instant().plus(LEASE)) > 0;
    }

    private String writeReport(SyncSummary summary) {
        try {
            return jsonMapper.writeValueAsString(summary);
        } catch (JacksonException e) {
            log.warn("Could not store the check report: {}", e.getMessage());
            return null;
        }
    }

    private SyncSummary readReport(String json) {
        try {
            return jsonMapper.readValue(json, SyncSummary.class);
        } catch (JacksonException e) {
            return null; // written by an older version: the next check replaces it
        }
    }

    /** Updates the mailbox the check started on; a mailbox connected since then is left alone. */
    private void updateConnection(Long connectionId, Instant syncedAt, String error) {
        connectionRepository.recordResult(connectionId, syncedAt, error, clock.instant());
    }


    /**
     * Gmail search for likely job mail (hiring senders, application wording, or a tracked company's
     * name) since the last complete sync (with an hour of overlap), or over the last N days.
     */
    String query(Instant lastSyncedAt, List<String> trackedCompanies) {
        String since = lastSyncedAt == null
                ? "newer_than:" + properties.initialSyncDays() + "d"
                : "after:" + lastSyncedAt.minus(Duration.ofHours(1)).getEpochSecond();
        return BASE_QUERY + " " + since + " " + JobEmailFilter.searchClause(trackedCompanies);
    }

    private static String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
