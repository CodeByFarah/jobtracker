package com.jobtrack.ai;

import com.jobtrack.config.AiProperties;
import com.jobtrack.exception.AiUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the local Ollama server. Every call asks for JSON that matches a schema, so the
 * model's answer can be parsed reliably instead of scraping free text.
 */
@Component
public class OllamaClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final AiProperties properties;

    public OllamaClient(AiProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        // A small model on a laptop CPU can take a while, especially on the first call (model load).
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.ollamaUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Sends a system + user prompt and returns the model's reply parsed as JSON.
     *
     * @param schema JSON schema the reply must follow (Ollama's structured outputs)
     */
    public JsonNode chatForJson(String systemPrompt, String userPrompt, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("stream", false);
        body.put("format", schema);
        // Keep the model in memory between the many calls of an email sync.
        body.put("keep_alive", "30m");
        body.put("options", Map.of("temperature", 0, "num_ctx", 8192));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));

        String raw;
        try {
            raw = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new AiUnavailableException("The local AI model isn't reachable at " + properties.ollamaUrl()
                    + ". Make sure Ollama is running and the model '" + properties.model() + "' is installed.", e);
        }
        try {
            String content = jsonMapper.readTree(raw).path("message").path("content").asString("");
            return jsonMapper.readTree(content);
        } catch (JacksonException e) {
            throw new AiUnavailableException("The AI model returned an answer that couldn't be read", e);
        }
    }
}
