package com.jobtrack.mapper;

import com.jobtrack.dto.task.TaskResponse;
import com.jobtrack.entity.FollowUpTask;
import com.jobtrack.entity.JobApplication;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(FollowUpTask task) {
        JobApplication application = task.getApplication();
        return new TaskResponse(
                task.getId(),
                application.getId(),
                application.getJobTitle(),
                application.getCompany().getName(),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.isCompleted(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
