package com.jobtrack.gmail;

import java.time.Instant;

/** The parts of a Gmail message the sync needs. {@code body} is plain text (HTML is converted). */
public record GmailMessage(String id, String from, String subject, Instant receivedAt, String body) {
}
