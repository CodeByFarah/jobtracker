package com.jobtrack.mapper;

import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.StatusHistoryResponse;
import com.jobtrack.entity.ApplicationStatusHistory;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;

public final class ApplicationMapper {

    private ApplicationMapper() {
    }

    public static ApplicationResponse toResponse(JobApplication application) {
        Company company = application.getCompany();
        return new ApplicationResponse(
                application.getId(),
                application.getJobTitle(),
                new ApplicationResponse.CompanySummary(company.getId(), company.getName()),
                application.getJobUrl(),
                application.getLocation(),
                application.getEmploymentType(),
                application.getSalaryMin(),
                application.getSalaryMax(),
                application.getSalaryCurrency(),
                application.getApplicationDate(),
                application.getStatus(),
                application.getStatus().allowedTransitions(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    public static StatusHistoryResponse toResponse(ApplicationStatusHistory history) {
        return new StatusHistoryResponse(history.getId(), history.getApplication().getId(),
                history.getPreviousStatus(), history.getNewStatus(), history.getChangedAt());
    }
}
