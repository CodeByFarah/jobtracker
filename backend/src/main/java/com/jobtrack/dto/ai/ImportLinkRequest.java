package com.jobtrack.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ImportLinkRequest(
        @NotBlank @Size(max = 500) @URL(regexp = "^https?:.*", message = "must be a valid http(s) URL")
        String url) {
}
