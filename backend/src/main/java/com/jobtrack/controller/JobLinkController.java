package com.jobtrack.controller;

import com.jobtrack.ai.JobLinkImportService;
import com.jobtrack.dto.ai.ImportLinkRequest;
import com.jobtrack.dto.ai.JobLinkPreview;
import com.jobtrack.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "AI", description = "Features powered by the local AI model")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class JobLinkController {

    private final JobLinkImportService importService;
    private final CurrentUser currentUser;

    public JobLinkController(JobLinkImportService importService, CurrentUser currentUser) {
        this.importService = importService;
        this.currentUser = currentUser;
    }

    @PostMapping("/api/applications/import-link")
    @Operation(summary = "Read job details from a posting link",
            description = "Fetches the page and fills in title, company, location, type and salary. Nothing is saved; "
                    + "create the application with POST /api/applications after the user has checked the values.")
    @ApiResponse(responseCode = "400", description = "Invalid link, or the page couldn't be read")
    @ApiResponse(responseCode = "503", description = "The local AI model isn't running")
    public JobLinkPreview importLink(@Valid @RequestBody ImportLinkRequest request) {
        return importService.preview(currentUser.id(), request.url());
    }
}
