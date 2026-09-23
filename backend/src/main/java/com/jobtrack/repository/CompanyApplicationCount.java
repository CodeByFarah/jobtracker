package com.jobtrack.repository;

/** Row of a "count applications grouped by company" query. */
public record CompanyApplicationCount(Long companyId, long count) {
}
