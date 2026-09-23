package com.jobtrack.controller;

import com.jobtrack.dto.task.TaskCompletionRequest;
import com.jobtrack.dto.task.TaskResponse;
import com.jobtrack.dto.task.UpdateTaskRequest;
import com.jobtrack.security.CurrentUser;
import com.jobtrack.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Follow-up tasks across all applications. Creating one happens under /api/applications/{id}/tasks. */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Follow-up tasks")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class TaskController {

    private final TaskService taskService;
    private final CurrentUser currentUser;

    public TaskController(TaskService taskService, CurrentUser currentUser) {
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List my tasks across all applications", description = "Soonest due first; undated tasks last.")
    public List<TaskResponse> list(
            @Parameter(description = "true = completed only, false = open only, omitted = all")
            @RequestParam(required = false) Boolean completed) {
        return taskService.listForUser(currentUser.id(), completed);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a task")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(currentUser.id(), id, request);
    }

    @PatchMapping("/{id}/completion")
    @Operation(summary = "Mark a task completed or re-open it",
            description = "Completing records completedAt; re-opening clears it.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public TaskResponse setCompletion(@PathVariable Long id, @Valid @RequestBody TaskCompletionRequest request) {
        return taskService.setCompleted(currentUser.id(), id, request.completed());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
