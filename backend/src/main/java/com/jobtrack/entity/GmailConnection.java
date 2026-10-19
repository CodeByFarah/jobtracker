package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A user's connected Gmail account. Only the (encrypted) refresh token is stored. */
@Entity
@Table(name = "gmail_connections")
public class GmailConnection extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false, unique = true)
    private User user;

    /** Read-only copy of the owner's id, so it can be used without loading the user. */
    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "gmail_address", nullable = false)
    private String gmailAddress;

    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "text")
    private String refreshTokenEncrypted;

    /** Start time of the last sync that completed; the next sync only reads mail newer than this. */
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_sync_error", columnDefinition = "text")
    private String lastSyncError;

    protected GmailConnection() {
    }

    public GmailConnection(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return userId;
    }

    public String getGmailAddress() {
        return gmailAddress;
    }

    public void setGmailAddress(String gmailAddress) {
        this.gmailAddress = gmailAddress;
    }

    public String getRefreshTokenEncrypted() {
        return refreshTokenEncrypted;
    }

    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) {
        this.refreshTokenEncrypted = refreshTokenEncrypted;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }

    public void setLastSyncError(String lastSyncError) {
        this.lastSyncError = lastSyncError;
    }
}
