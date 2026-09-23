package com.jobtrack.dto.application;

import com.jobtrack.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull ApplicationStatus status) {
}
