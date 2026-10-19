package com.jobtrack.dto.gmail;

import java.time.Instant;

/**
 * Result of the most recent sync run.
 *
 * @param messagesScanned new emails looked at
 * @param emailsAnalyzed  emails that mentioned a tracked company and were read by the AI
 * @param statusesUpdated applications whose status the AI changed
 * @param error           why the run stopped early, or null
 */
public record SyncSummary(Instant startedAt, Instant finishedAt, int messagesScanned, int emailsAnalyzed,
                          int statusesUpdated, String error) {
}
