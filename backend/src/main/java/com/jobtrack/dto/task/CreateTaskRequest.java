package com.jobtrack.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank @Size(max = 150)
        String title,

        @Size(max = 5_000)
        String description,

        LocalDate dueDate) {
}
