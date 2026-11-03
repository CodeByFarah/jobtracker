package com.jobtrack.ai;

import com.jobtrack.config.AiProperties;
import com.jobtrack.exception.AiUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
 * Talks to the AI model: the local Ollama server by default, or an online service that speaks the
 * OpenAI chat-completions API (see {@link AiProperties}). Every call asks for JSON that matches a
 * schema, so the model's answer can be parsed reliably instead of scraping free text.
 */
@Component
public class OllamaClient {

    /** Waits before retrying when an online service says "too many requests". */
    private static final long[] RATE_LIMIT_WAITS_MS = {3_000, 10_000, 30_000};

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final AiProperties properties;
    /** Set once the service has refused a JSON schema; from then on plain JSON mode is used. */
    private volatile boolean plainJsonMode;

    public OllamaClient(AiProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        // A small model on a laptop CPU can take a while, especially on the first call (model load).
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.endpoint())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Sends a system + user prompt and returns the model's reply parsed as JSON.
     *
     * @param schema JSON schema the reply must follow
     */
    public JsonNode chatForJson(String systemPrompt, String userPrompt, Map<String, Object> schema) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt));
        String content = properties.online()
                ? onlineChat(messages, schema)
                : ollamaChat(messages, schema);
        try {
            return jsonMapper.readTree(stripCodeFence(content));
        } catch (JacksonException e) {
            throw new AiUnavailableException("The AI model returned an answer that couldn't be read", e);
        }
    }

    private String ollamaChat(List<Map<String, String>> messages, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("stream", false);
        body.put("format", schema);
        // Keep the model in memory between the many calls of an email sync.
        body.put("keep_alive", "30m");
        body.put("options", Map.of("temperature", 0, "num_ctx", 8192));
        body.put("messages", messages);
        try {
            String raw = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(raw).path("message").path("content").asString("");
        } catch (RestClientException e) {
            throw new AiUnavailableException("The local AI model isn't reachable at " + properties.ollamaUrl()
                    + ". Make sure Ollama is running and the model '" + properties.model() + "' is installed.", e);
        } catch (JacksonException e) {
            throw new AiUnavailableException("The AI model returned an answer that couldn't be read", e);
        }
    }

    private String onlineChat(List<Map<String, String>> messages, Map<String, Object> schema) {
        if (properties.apiKey().isBlank()) {
            throw new AiUnavailableException("The AI service isn't set up yet (no API key).", null);
        }
        String json = jsonMapper.writeValueAsString(onlineBody(messages, schema, !plainJsonMode));

        for (int attempt = 0; ; attempt++) {
            try {
                String raw = restClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json)
                        .retrieve()
                        .body(String.class);
                return jsonMapper.readTree(raw).path("choices").path(0).path("message").path("content").asString("");
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt < RATE_LIMIT_WAITS_MS.length) {
                    pause(RATE_LIMIT_WAITS_MS[attempt]);
                    continue;
                }
                throw new AiUnavailableException("The AI service is busy right now. Please try again in a few minutes.", e);
            } catch (HttpClientErrorException.BadRequest e) {
                // Some services (DeepSeek, for one) don't accept a JSON schema in response_format.
                // Fall back to plain JSON mode with the schema written into the instructions.
                if (!plainJsonMode) {
                    plainJsonMode = true;
                    json = jsonMapper.writeValueAsString(onlineBody(messages, schema, false));
                    attempt--;
                    continue;
                }
                throw new AiUnavailableException("The AI service refused the request: "
                        + e.getResponseBodyAsString(), e);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new AiUnavailableException("The AI service rejected the API key.", e);
            } catch (RestClientException e) {
                throw new AiUnavailableException("The AI service at " + properties.apiUrl()
                        + " couldn't be reached or failed: " + e.getMessage(), e);
            } catch (JacksonException e) {
                throw new AiUnavailableException("The AI model returned an answer that couldn't be read", e);
            }
        }
    }

    private Map<String, Object> onlineBody(List<Map<String, String>> messages, Map<String, Object> schema,
                                           boolean withSchema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("temperature", 0);
        if (withSchema) {
            body.put("messages", messages);
            body.put("response_format", Map.of("type", "json_schema",
                    "json_schema", Map.of("name", "answer", "schema", schema, "strict", false)));
        } else {
            String rules = "\n\nReply with a single JSON object only, matching this JSON schema:\n"
                    + jsonMapper.writeValueAsString(schema);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", messages.get(0).get("content") + rules),
                    messages.get(1)));
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    /** Some models wrap JSON in ```json fences even when asked not to. */
    static String stripCodeFence(String content) {
        String text = content == null ? "" : content.strip();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return text;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted while waiting for the AI service", e);
        }
    }
}
