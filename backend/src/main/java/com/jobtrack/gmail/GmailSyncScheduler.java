package com.jobtrack.gmail;

import com.jobtrack.repository.GmailConnectionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks every connected mailbox periodically (default every 30 minutes, GMAIL_SYNC_INTERVAL). */
@Component
public class GmailSyncScheduler {

    private final GmailConnectionRepository connectionRepository;
    private final EmailSyncService syncService;

    public GmailSyncScheduler(GmailConnectionRepository connectionRepository, EmailSyncService syncService) {
        this.connectionRepository = connectionRepository;
        this.syncService = syncService;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${jobtrack.gmail.sync-interval:PT30M}")
    void syncAllConnectedMailboxes() {
        connectionRepository.findAllUserIds().forEach(syncService::requestSync);
    }
}
