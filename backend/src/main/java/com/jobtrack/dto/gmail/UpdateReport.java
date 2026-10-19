package com.jobtrack.dto.gmail;

import java.util.List;

/** What "Check for Updates" shows: the last check's summary and the status of every application. */
public record UpdateReport(SyncSummary lastCheck, List<ApplicationUpdate> applications) {
}
