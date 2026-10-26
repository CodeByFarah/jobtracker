package com.jobtrack.dto.user;

import jakarta.validation.constraints.NotBlank;

/** The account password, so a stolen or forgotten session can't delete everything. */
public record DeleteAccountRequest(@NotBlank String password) {
}
