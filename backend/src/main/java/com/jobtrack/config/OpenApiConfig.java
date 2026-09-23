package com.jobtrack.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. Every operation requires the bearer token unless it is annotated with an
 * empty {@code @SecurityRequirements} (register and login).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI jobTrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JobTrack API")
                        .version("1.0")
                        .description("""
                                REST API for managing a job search: companies, applications, status \
                                history, interviews, follow-up tasks and dashboard statistics.

                                Authenticate with `POST /api/auth/login` (or register), then click \
                                **Authorize** and paste the returned `accessToken`.

                                Errors use a common body: `{timestamp, status, error, message, path, \
                                fieldErrors?}`. Resources owned by another user respond with 404."""))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
