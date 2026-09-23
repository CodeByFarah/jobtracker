package com.jobtrack.dto.task;

import jakarta.validation.constraints.NotNull;

public record TaskCompletionRequest(@NotNull Boolean completed) {
}
