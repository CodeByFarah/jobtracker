package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gmail integration settings, bound from {@code jobtrack.gmail.*}. The client id and secret come
 * from an OAuth client created in Google Cloud; without them the integration is simply disabled.
 */
@ConfigurationProperties(prefix = "jobtrack.gmail")
public record GmailProperties(String clientId,
                              String clientSecret,
                              String redirectUri,
                              String frontendUrl,
                              int initialSyncDays,
                              int maxMessagesPerSync) {

    public GmailProperties {
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://localhost:5173/api/gmail/oauth/callback";
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            frontendUrl = "http://localhost:5173";
        }
        frontendUrl = frontendUrl.replaceAll("/+$", "");
        if (initialSyncDays <= 0) {
            initialSyncDays = 180;
        }
        if (maxMessagesPerSync <= 0) {
            maxMessagesPerSync = 500;
        }
    }

    /** True once both OAuth credentials are set. */
    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
