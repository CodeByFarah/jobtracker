package com.jobtrack.dto.gmail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The {@code code} and {@code state} Google sent back after sign-in. */
public record CompleteConnectionRequest(@NotBlank @Size(max = 2048) String code,
                                        @NotBlank @Size(max = 1024) String state) {
}