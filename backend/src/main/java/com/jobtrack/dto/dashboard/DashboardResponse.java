package com.jobtrack.dto.dashboard;

import com.jobtrack.dto.interview.InterviewResponse;
import com.jobtrack.dto.task.TaskResponse;
import com.jobtrack.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Every number here is computed from the database at request time. */
public record DashboardResponse(
        long totalApplications,
        @Schema(description = "Applications whose application date falls in the current calendar month")
        long applicationsThisMonth,
        @Schema(description = "Applications in APPLIED, SCREENING, INTERVIEW or OFFER")
        long activeApplications,
        long totalInterviews,
        long upcomingInterviewCount,
        @Schema(description = "Applications that ever reached OFFER (per status history)")
        long offers,
        long rejected,
        long outstandingTasks,
        long overdueTasks,
        @Schema(description = "Count per status, in pipeline order, including zero counts")
        List<StatusCountResponse> statusBreakdown,
        @Schema(description = "Applications per month (by application date) for the last 6 months")
        List<MonthCountResponse> applicationsPerMonth,
        List<InterviewResponse> upcomingInterviews,
        List<TaskResponse> upcomingTasks) {

    public record StatusCountResponse(ApplicationStatus status, long count) {
    }

    @Schema(description = "month formatted as yyyy-MM")
    public record MonthCountResponse(String month, long count) {
    }
}
