package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sending email (used for "forgot password" links), bound from {@code jobtrack.mail.*}.
 * <ul>
 *   <li>{@code provider=none} (default): no email; "forgot password" says it isn't available.</li>
 *   <li>{@code provider=brevo}: Brevo's HTTP API (works where SMTP ports are blocked, like Render's free plan).</li>
 *   <li>{@code provider=log}: writes the email to the server log instead. For local development only.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jobtrack.mail")
public record MailProperties(String provider, String apiKey, String fromEmail, String fromName, String appUrl) {

    public static final String NONE = "none";
    public static final String BREVO = "brevo";
    public static final String LOG = "log";

    public MailProperties {
        provider = provider == null || provider.isBlank() ? NONE : provider.strip().toLowerCase();
        if (!provider.equals(NONE) && !provider.equals(BREVO) && !provider.equals(LOG)) {
            throw new IllegalArgumentException("jobtrack.mail.provider must be none, brevo or log, not '" + provider + "'");
        }
        apiKey = apiKey == null ? "" : apiKey.strip();
        fromEmail = fromEmail == null ? "" : fromEmail.strip();
        fromName = fromName == null || fromName.isBlank() ? "JobTrack" : fromName.strip();
        appUrl = appUrl == null || appUrl.isBlank() ? "http://localhost:5173" : appUrl.strip().replaceAll("/+$", "");
    }

    /** Whether emails can actually be sent. */
    public boolean enabled() {
        return switch (provider) {
            case BREVO -> !apiKey.isEmpty() && !fromEmail.isEmpty();
            case LOG -> true;
            default -> false;
        };
    }
}
