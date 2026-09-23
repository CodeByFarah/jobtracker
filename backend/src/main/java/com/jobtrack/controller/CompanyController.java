package com.jobtrack.controller;

import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.company.CompanyRequest;
import com.jobtrack.dto.company.CompanyResponse;
import com.jobtrack.security.CurrentUser;
import com.jobtrack.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/companies")
@Tag(name = "Companies", description = "Companies the user is tracking. Only the owner can see or change them.")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentUser currentUser;

    public CompanyController(CompanyService companyService, CurrentUser currentUser) {
        this.companyService = companyService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List my companies", description = "Sorted by name; each includes its number of applications.")
    public List<CompanyResponse> list(
            @Parameter(description = "Case-insensitive name search") @RequestParam(required = false) String q) {
        return companyService.list(currentUser.id(), q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a company")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public CompanyResponse get(@PathVariable Long id) {
        return companyService.get(currentUser.id(), id);
    }

    @PostMapping
    @Operation(summary = "Create a company")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "A company with this name already exists")
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        CompanyResponse created = companyService.create(currentUser.id(), request);
        return ResponseEntity.created(locationOf(created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a company")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    @ApiResponse(responseCode = "409", description = "A company with this name already exists")
    public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return companyService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a company", description = "Only allowed when the company has no applications.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    @ApiResponse(responseCode = "409", description = "The company still has applications")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        companyService.delete(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/applications")
    @Operation(summary = "List a company's applications", description = "Most recently updated first.")
    @ApiResponse(responseCode = "404", description = "Not found or not owned by the caller")
    public List<ApplicationResponse> applications(@PathVariable Long id) {
        return companyService.listApplications(currentUser.id(), id);
    }

    private static URI locationOf(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
