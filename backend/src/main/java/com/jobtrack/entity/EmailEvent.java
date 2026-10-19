package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One email the AI read, what it decided the email means, and whether it changed an application's
 * status. Kept so the user can see why a status changed, and so no email is processed twice.
 */
@Entity
@Table(name = "email_events")
public class EmailEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @Column(name = "gmail_message_id", nullable = false, updatable = false)
    private String gmailMessageId;

    private String sender;

    private String subject;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailCategory category;

    private Double confidence;

    @Column(columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before")
    private ApplicationStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after")
    private ApplicationStatus statusAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailEvent() {
    }

    public EmailEvent(User user, String gmailMessageId, Instant receivedAt, EmailCategory category) {
        this.user = user;
        this.gmailMessageId = gmailMessageId;
        this.receivedAt = receivedAt;
        this.category = category;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public JobApplication getApplication() {
        return application;
    }

    public void setApplication(JobApplication application) {
        this.application = application;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public EmailCategory getCategory() {
        return category;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public ApplicationStatus getStatusBefore() {
        return statusBefore;
    }

    public void setStatusBefore(ApplicationStatus statusBefore) {
        this.statusBefore = statusBefore;
    }

    public ApplicationStatus getStatusAfter() {
        return statusAfter;
    }

    public void setStatusAfter(ApplicationStatus statusAfter) {
        this.statusAfter = statusAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
