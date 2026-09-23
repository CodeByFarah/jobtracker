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
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One immutable entry in an application's status timeline. {@code previousStatus} is null for
 * the entry written when the application is first created.
 */
@Entity
@Table(name = "application_status_history")
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false)
    private ApplicationStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false)
    private ApplicationStatus newStatus;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ApplicationStatusHistory() {
    }

    public ApplicationStatusHistory(JobApplication application, ApplicationStatus previousStatus,
                                    ApplicationStatus newStatus, Instant changedAt) {
        this.application = application;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public JobApplication getApplication() {
        return application;
    }

    public ApplicationStatus getPreviousStatus() {
        return previousStatus;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
