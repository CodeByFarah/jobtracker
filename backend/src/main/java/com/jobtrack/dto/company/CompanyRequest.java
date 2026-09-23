package com.jobtrack.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Body for both creating (POST) and replacing (PUT) a company. The two operations accept exactly
 * the same fields, so a single request type avoids two identical classes drifting apart.
 */
public record CompanyRequest(
        @NotBlank @Size(max = 150)
        String name,

        @Size(max = 500) @URL(regexp = "^https?:.*", message = "must be a valid http(s) URL")
        String website,

        @Size(max = 100)
        String industry,

        @Size(max = 150)
        String location,

        @Size(max = 10_000)
        String notes) {
}
