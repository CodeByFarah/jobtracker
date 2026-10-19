package com.jobtrack.dto.gmail;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Result of the most recent check.
 *
 * @param messagesScanned new emails looked at
 * @param emailsAnalyzed  emails that mentioned a tracked company and were read by the AI
 * @param statusesUpdated applications whose status the AI changed
 * @param error           why the run stopped early, or null
 */
public record SyncSummary(Instant startedAt, Instant finishedAt, int messagesScanned, int emailsAnalyzed,
                          int statusesUpdated, String error,
                          @Schema(description = "Every status change made in this check")
                          List<StatusChange> changes,
                          @Schema(description = "Short summary written by the AI (or a plain fallback)")
                          String overview) {
}
