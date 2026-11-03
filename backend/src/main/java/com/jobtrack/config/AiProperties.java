package com.jobtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the AI model, bound from {@code jobtrack.ai.*}.
 * <ul>
 *   <li>{@code provider=ollama} (default): a model running in Ollama on this machine. Free, no API key,
 *       nothing leaves the computer.</li>
 *   <li>{@code provider=openai}: any online service that speaks the OpenAI chat-completions API
 *       (Groq, OpenAI, Fireworks, OpenRouter...), for servers that can't run a local model.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jobtrack.ai")
public record AiProperties(String provider, String ollamaUrl, String apiUrl, String apiKey, String model,
                           Duration timeout) {

    public static final String OLLAMA = "ollama";
    public static final String OPENAI = "openai";

    public AiProperties {
        provider = provider == null || provider.isBlank() ? OLLAMA : provider.strip().toLowerCase();
        if (!provider.equals(OLLAMA) && !provider.equals(OPENAI)) {
            throw new IllegalArgumentException("jobtrack.ai.provider must be 'ollama' or 'openai', not '" + provider + "'");
        }
        if (ollamaUrl == null || ollamaUrl.isBlank()) {
            ollamaUrl = "http://localhost:11434";
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "https://api.groq.com/openai/v1";
        }
        apiUrl = apiUrl.replaceAll("/+$", "");
        if (apiKey == null) {
            apiKey = "";
        }
        if (model == null || model.isBlank()) {
            model = provider.equals(OPENAI) ? "openai/gpt-oss-20b" : "qwen3:4b-instruct-2507-q4_K_M";
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofMinutes(3);
        }
    }

    public boolean online() {
        return provider.equals(OPENAI);
    }

    /** Where requests go, for messages (never includes the key). */
    public String endpoint() {
        return online() ? apiUrl : ollamaUrl;
    }
}
