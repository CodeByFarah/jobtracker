package com.jobtrack.controller;

import com.jobtrack.dto.interview.InterviewRequest;
import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.security.CurrentUser;
import com.jobtrack.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Interviews across all applications. Creating one happens under /api/applications/{id}/interviews. */
@RestController
@RequestMapping("/api/interviews")
@Tag(name = "Interviews")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class InterviewController {

    private final InterviewService interviewService;
    private final CurrentUser currentUser;

    public InterviewController(InterviewService interviewService, CurrentUser currentUser) {
        this.interviewService = interviewService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List my interviews across all applications")
    public List<InterviewResponse> list(
            @Parameter(description = "UPCOMING: pending and in the future (soonest first); PAST: everything else (latest first)")
            @RequestParam(defaultValue = "ALL") InterviewService.Scope scope) {
        return interviewService.listForUser(currentUser.id(), scope);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an interview")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    @ApiResponse(responseCode = "409", description = "The application is closed")
    public InterviewResponse update(@PathVariable Long id, @Valid @RequestBody InterviewRequest request) {
        return interviewService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an interview")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interviewService.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
