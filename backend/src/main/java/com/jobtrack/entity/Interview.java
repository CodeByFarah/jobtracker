package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "interviews")
public class Interview extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false)
    private InterviewType type;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "interviewer_name")
    private String interviewerName;

    /** Office address or video-call link. */
    private String location;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterviewStatus status;

    protected Interview() {
    }

    public Interview(JobApplication application, InterviewType type, Instant scheduledAt, InterviewStatus status) {
        this.application = application;
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    public JobApplication getApplication() {
        return application;
    }

    public InterviewType getType() {
        return type;
    }

    public void setType(InterviewType type) {
        this.type = type;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getInterviewerName() {
        return interviewerName;
    }

    public void setInterviewerName(String interviewerName) {
        this.interviewerName = interviewerName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public void setStatus(InterviewStatus status) {
        this.status = status;
    }
}
