package com.jobtrack.controller;

import com.jobtrack.dto.dashboard.DashboardResponse;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.security.CurrentUser;
import com.jobtrack.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Job-search statistics", description = "All figures are computed from the database on each request.")
    @ApiResponse(responseCode = "400", description = "Unknown time zone")
    public DashboardResponse get(
            @Parameter(description = "IANA time zone used for 'today' and 'this month', e.g. Europe/London. Defaults to UTC.")
            @RequestParam(required = false) String timezone) {
        return dashboardService.getDashboard(currentUser.id(), parseZone(timezone));
    }

    private static ZoneId parseZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new BadRequestException("timezone", "Unknown time zone '" + timezone + "'");
        }
    }
}
