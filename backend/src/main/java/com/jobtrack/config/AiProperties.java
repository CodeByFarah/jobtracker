package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the local AI model, bound from {@code jobtrack.ai.*}. The model runs in Ollama on
 * the same machine, so there is no API key and no per-request cost.
 */
@ConfigurationProperties(prefix = "jobtrack.ai")
public record AiProperties(String ollamaUrl, String model, Duration timeout) {

    public AiProperties {
        if (ollamaUrl == null || ollamaUrl.isBlank()) {
            ollamaUrl = "http://localhost:11434";
        }
        if (model == null || model.isBlank()) {
            model = "qwen3:4b-instruct-2507-q4_K_M";
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofMinutes(3);
        }
    }
}
