package com.jobtrack.controller;

import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.ApplicationSearchCriteria;
import com.jobtrack.dto.application.ApplicationSortField;
import com.jobtrack.dto.application.ChangeStatusRequest;
import com.jobtrack.dto.application.CreateApplicationRequest;
import com.jobtrack.dto.application.StatusHistoryResponse;
import com.jobtrack.dto.application.UpdateApplicationRequest;
import com.jobtrack.dto.common.PageResponse;
import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.dto.task.CreateTaskRequest;
import com.jobtrack.dto.task.TaskResponse;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmploymentType;
import com.jobtrack.security.CurrentUser;
import com.jobtrack.service.ApplicationService;
import com.jobtrack.service.InterviewService;
import com.jobtrack.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Job applications, their status history, interviews and tasks")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final InterviewService interviewService;
    private final TaskService taskService;
    private final CurrentUser currentUser;

    public ApplicationController(ApplicationService applicationService, InterviewService interviewService,
                                 TaskService taskService, CurrentUser currentUser) {
        this.applicationService = applicationService;
        this.interviewService = interviewService;
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Search, filter, sort and paginate my applications",
            description = "All filters are optional and combined with AND. Pages are zero-based.")
    @ApiResponse(responseCode = "400", description = "Invalid filter, sort or paging parameter")
    public PageResponse<ApplicationResponse> search(
            @Parameter(description = "Matches job title or company name (case-insensitive)")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ApplicationStatus status,
            @Parameter(description = "Case-insensitive substring of the job location")
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "APPLICATION_DATE") ApplicationSortField sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        // Secondary sort on id keeps page boundaries stable when the primary values tie.
        Sort order = Sort.by(new Sort.Order(direction, sort.property()).nullsLast(), Sort.Order.desc("id"));
        return applicationService.search(currentUser.id(),
                new ApplicationSearchCriteria(q, status, location, employmentType, companyId),
                PageRequest.of(page, size, order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an application")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public ApplicationResponse get(@PathVariable Long id) {
        return applicationService.get(currentUser.id(), id);
    }

    @PostMapping
    @Operation(summary = "Create an application",
            description = "Also writes the first status-history entry. Status defaults to SAVED.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Company not found or not owned by the caller")
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse created = applicationService.create(currentUser.id(), request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an application's details",
            description = "Replaces the editable details. Use PATCH /{id}/status to change the status.")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Application or company not found")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateApplicationRequest request) {
        return applicationService.update(currentUser.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change an application's status",
            description = "Validates the transition, updates the status and appends a history entry in one transaction.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    @ApiResponse(responseCode = "409", description = "Transition not allowed from the current status")
    public ApplicationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        return applicationService.changeStatus(currentUser.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application", description = "Also deletes its history, interviews and tasks.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        applicationService.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get the status history", description = "Chronological, oldest first.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public List<StatusHistoryResponse> history(@PathVariable Long id) {
        return applicationService.getHistory(currentUser.id(), id);
    }

    @GetMapping("/{id}/interviews")
    @Operation(summary = "List an application's interviews", description = "Ordered by scheduled time.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public List<InterviewResponse> interviews(@PathVariable Long id) {
        return interviewService.listForApplication(currentUser.id(), id);
    }

    @PostMapping("/{id}/interviews")
    @Operation(summary = "Add an interview to an application")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed (e.g. a scheduled interview in the past)")
    @ApiResponse(responseCode = "404", description = "Application not found or not owned by the caller")
    @ApiResponse(responseCode = "409", description = "The application is closed")
    public ResponseEntity<InterviewResponse> createInterview(@PathVariable Long id,
                                                             @Valid @RequestBody InterviewRequest request) {
        InterviewResponse created = interviewService.create(currentUser.id(), id, request);
        return ResponseEntity.created(resourceUri("/api/interviews/{id}", created.id())).body(created);
    }

    @GetMapping("/{id}/tasks")
    @Operation(summary = "List an application's follow-up tasks", description = "Open tasks first, by due date.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public List<TaskResponse> tasks(@PathVariable Long id) {
        return taskService.listForApplication(currentUser.id(), id);
    }

    @PostMapping("/{id}/tasks")
    @Operation(summary = "Add a follow-up task to an application")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Application not found or not owned by the caller")
    public ResponseEntity<TaskResponse> createTask(@PathVariable Long id, @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.create(currentUser.id(), id, request);
        return ResponseEntity.created(resourceUri("/api/tasks/{id}", created.id())).body(created);
    }

    private static URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }

    private static URI resourceUri(String path, Long id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).buildAndExpand(id).toUri();
    }
}
