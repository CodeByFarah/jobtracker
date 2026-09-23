package com.jobtrack.repository;

import com.jobtrack.entity.ApplicationStatus;

/** Row of a "count applications grouped by status" query. */
public record StatusCount(ApplicationStatus status, long count) {
}
