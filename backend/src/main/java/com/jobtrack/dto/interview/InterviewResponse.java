package com.jobtrack.dto.interview;

import com.jobtrack.entity.InterviewStatus;
import com.jobtrack.entity.InterviewType;

import java.time.Instant;

public record InterviewResponse(
        Long id,
        Long applicationId,
        String jobTitle,
        String companyName,
        InterviewType type,
        Instant scheduledAt,
        Integer durationMinutes,
        String interviewerName,
        String location,
        String notes,
        InterviewStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
