package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class FollowUpTask extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected FollowUpTask() {
    }

    public FollowUpTask(JobApplication application, String title) {
        this.application = application;
        this.title = title;
    }

    /**
     * Keeps {@code completed} and {@code completedAt} consistent (also enforced by a DB check
     * constraint). Re-completing an already completed task keeps the original timestamp.
     */
    public void setCompleted(boolean completed, Instant now) {
        if (completed && !this.completed) {
            this.completedAt = now;
        } else if (!completed) {
            this.completedAt = null;
        }
        this.completed = completed;
    }

    public JobApplication getApplication() {
        return application;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
