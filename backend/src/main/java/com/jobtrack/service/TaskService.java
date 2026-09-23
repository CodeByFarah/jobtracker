package com.jobtrack.service;

import com.jobtrack.dto.task.CreateTaskRequest;
import com.jobtrack.dto.task.TaskResponse;
import com.jobtrack.dto.task.UpdateTaskRequest;
import com.jobtrack.entity.FollowUpTask;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.mapper.TaskMapper;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.repository.FollowUpTaskRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class TaskService {

    private final FollowUpTaskRepository taskRepository;
    private final ApplicationService applicationService;
    private final Clock clock;

    public TaskService(FollowUpTaskRepository taskRepository, ApplicationService applicationService, Clock clock) {
        this.taskRepository = taskRepository;
        this.applicationService = applicationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listForApplication(Long userId, Long applicationId) {
        applicationService.getOwnedApplication(userId, applicationId);
        return taskRepository.findForApplication(applicationId).stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    /**
     * The user's tasks across all applications, soonest due first.
     *
     * @param completed null for all tasks, otherwise only completed / only open tasks
     */
    @Transactional(readOnly = true)
    public List<TaskResponse> listForUser(Long userId, Boolean completed) {
        List<FollowUpTask> tasks = completed == null
                ? taskRepository.findAllForUser(userId)
                : taskRepository.findForUserByCompleted(userId, completed, Pageable.unpaged());
        return tasks.stream().map(TaskMapper::toResponse).toList();
    }

    @Transactional
    public TaskResponse create(Long userId, Long applicationId, CreateTaskRequest request) {
        JobApplication application = applicationService.getOwnedApplication(userId, applicationId);
        FollowUpTask task = new FollowUpTask(application, TextUtils.clean(request.title()));
        task.setDescription(TextUtils.clean(request.description()));
        task.setDueDate(request.dueDate());
        return TaskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest request) {
        FollowUpTask task = getOwnedTask(userId, taskId);
        task.setTitle(TextUtils.clean(request.title()));
        task.setDescription(TextUtils.clean(request.description()));
        task.setDueDate(request.dueDate());
        task.setCompleted(request.completed(), clock.instant());
        taskRepository.flush();
        return TaskMapper.toResponse(task);
    }

    /** Marks a task done (recording when) or re-opens it (clearing the completion time). */
    @Transactional
    public TaskResponse setCompleted(Long userId, Long taskId, boolean completed) {
        FollowUpTask task = getOwnedTask(userId, taskId);
        task.setCompleted(completed, clock.instant());
        taskRepository.flush();
        return TaskMapper.toResponse(task);
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        taskRepository.delete(getOwnedTask(userId, taskId));
    }

    private FollowUpTask getOwnedTask(Long userId, Long taskId) {
        return taskRepository.findByIdAndApplicationUserId(taskId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }
}
