package com.jobtrack.controller;

import com.jobtrack.config.GmailProperties;
import com.jobtrack.dto.gmail.ConnectUrlResponse;
import com.jobtrack.dto.gmail.EmailEventResponse;
import com.jobtrack.dto.gmail.GmailStatusResponse;
import com.jobtrack.entity.GmailConnection;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.GmailAuthException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.gmail.EmailEventService;
import com.jobtrack.gmail.EmailSyncService;
import com.jobtrack.gmail.GmailConnectionService;
import com.jobtrack.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@Tag(name = "Gmail", description = "Connect Gmail so the AI can track application emails")
public class GmailController {

    private static final Logger log = LoggerFactory.getLogger(GmailController.class);

    private final GmailConnectionService connectionService;
    private final EmailSyncService syncService;
    private final EmailEventService eventService;
    private final GmailProperties properties;
    private final CurrentUser currentUser;

    public GmailController(GmailConnectionService connectionService, EmailSyncService syncService,
                           EmailEventService eventService, GmailProperties properties, CurrentUser currentUser) {
        this.connectionService = connectionService;
        this.syncService = syncService;
        this.eventService = eventService;
        this.properties = properties;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/gmail")
    @Operation(summary = "Gmail connection and sync status")
    public GmailStatusResponse status() {
        return statusFor(currentUser.id());
    }

    @GetMapping("/api/gmail/connect-url")
    @Operation(summary = "Google sign-in URL", description = "Send the browser here to connect Gmail (read-only access).")
    @ApiResponse(responseCode = "409", description = "Google OAuth credentials are not configured on the server")
    public ConnectUrlResponse connectUrl() {
        return new ConnectUrlResponse(connectionService.connectUrl(currentUser.id()));
    }

    /**
     * Google redirects the browser here after sign-in. There is no bearer token on this request:
     * the signed {@code state} identifies the user. Always redirects back to the frontend.
     */
    @GetMapping("/api/gmail/oauth/callback")
    @Operation(summary = "OAuth callback (called by Google)")
    public ResponseEntity<Void> oauthCallback(@RequestParam(required = false) String code,
                                              @RequestParam(required = false) String state,
                                              @RequestParam(required = false) String error) {
        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(properties.frontendUrl() + "/profile");
        if (error != null || code == null || state == null) {
            target.queryParam("gmail", "error")
                    .queryParam("reason", error != null ? "Google sign-in was cancelled." : "Google sent an incomplete response.");
        } else {
            try {
                long userId = connectionService.completeConnection(code, state);
                syncService.requestSync(userId);
                target.queryParam("gmail", "connected");
            } catch (BadRequestException | GmailAuthException e) {
                target.queryParam("gmail", "error").queryParam("reason", e.getMessage());
            } catch (RestClientException e) {
                log.warn("Gmail connection failed: {}", e.getMessage());
                target.queryParam("gmail", "error").queryParam("reason", "Couldn't reach Google. Please try again.");
            }
        }
        URI location = target.encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @PostMapping("/api/gmail/sync")
    @Operation(summary = "Check Gmail now", description = "Starts a background sync and returns immediately.")
    @ApiResponse(responseCode = "202", description = "Sync started (or already running)")
    @ApiResponse(responseCode = "404", description = "Gmail is not connected")
    public ResponseEntity<GmailStatusResponse> sync() {
        Long userId = currentUser.id();
        if (connectionService.find(userId).isEmpty()) {
            throw new ResourceNotFoundException("Gmail is not connected");
        }
        syncService.requestSync(userId);
        return ResponseEntity.accepted().body(statusFor(userId));
    }

    @DeleteMapping("/api/gmail")
    @Operation(summary = "Disconnect Gmail", description = "Revokes access at Google and deletes the stored token. "
            + "Emails already processed stay on the applications.")
    @ApiResponse(responseCode = "204", description = "Disconnected")
    public ResponseEntity<Void> disconnect() {
        connectionService.disconnect(currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/gmail/events")
    @Operation(summary = "Recent emails the AI matched to applications", description = "Newest first.")
    public List<EmailEventResponse> recentEvents(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return eventService.recent(currentUser.id(), limit);
    }

    @GetMapping("/api/applications/{id}/emails")
    @Operation(summary = "Emails the AI matched to this application", description = "Newest first.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public List<EmailEventResponse> applicationEmails(@PathVariable Long id) {
        return eventService.forApplication(currentUser.id(), id);
    }

    private GmailStatusResponse statusFor(Long userId) {
        GmailConnection connection = connectionService.find(userId).orElse(null);
        return new GmailStatusResponse(
                connectionService.isConfigured(),
                connection != null,
                connection == null ? null : connection.getGmailAddress(),
                connection == null ? null : connection.getLastSyncedAt(),
                connection == null ? null : connection.getLastSyncError(),
                syncService.isSyncing(userId),
                syncService.lastRun(userId).orElse(null));
    }
}
