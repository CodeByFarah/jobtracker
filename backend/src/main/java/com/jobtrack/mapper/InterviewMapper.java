package com.jobtrack.mapper;

import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.entity.Interview;
import com.jobtrack.entity.JobApplication;

public final class InterviewMapper {

    private InterviewMapper() {
    }

    public static InterviewResponse toResponse(Interview interview) {
        JobApplication application = interview.getApplication();
        return new InterviewResponse(
                interview.getId(),
                application.getId(),
                application.getJobTitle(),
                application.getCompany().getName(),
                interview.getType(),
                interview.getScheduledAt(),
                interview.getDurationMinutes(),
                interview.getInterviewerName(),
                interview.getLocation(),
                interview.getNotes(),
                interview.getStatus(),
                interview.getCreatedAt(),
                interview.getUpdatedAt());
    }
}
