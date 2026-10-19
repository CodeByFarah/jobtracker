package com.jobtrack.dto.gmail;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record GmailStatusResponse(
        @Schema(description = "False when the server has no Google OAuth credentials configured")
        boolean configured,
        boolean connected,
        String gmailAddress,
        Instant lastSyncedAt,
        String lastSyncError,
        @Schema(description = "A check is running or queued right now")
        boolean syncing,
        @Schema(description = "While checking: emails processed so far")
        int progressDone,
        @Schema(description = "While checking: new emails to look at (0 until they have been listed)")
        int progressTotal,
        SyncSummary lastRun) {
}
