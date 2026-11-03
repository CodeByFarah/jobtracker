package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    /** Stored lower-cased so the unique constraint is effectively case-insensitive. */
    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash - the plain-text password is never stored. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String headline;

    private String location;

    /** When the password last changed; tokens issued before it are no longer accepted. */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    protected User() {
    }

    /** Sets a new password (already hashed) and records when, which signs out older sessions. */
    public void changePassword(String newPasswordHash, Instant changedAt) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = changedAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
