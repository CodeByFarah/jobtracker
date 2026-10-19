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
        @Schema(description = "A sync is running or queued right now")
        boolean syncing,
        SyncSummary lastRun) {
}
