package com.jobtrack.ai;

import com.jobtrack.config.AiProperties;
import com.jobtrack.exception.AiUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The online (OpenAI-compatible) mode, against a small fake server on this machine. */
class OllamaClientOnlineTest {

    private static final Map<String, Object> SCHEMA = Map.of("type", "object",
            "properties", Map.of("category", Map.of("type", "string")));

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OllamaClient client(String key) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        return new OllamaClient(new AiProperties("openai", null, url, key, "test-model", Duration.ofSeconds(5)),
                JsonMapper.builder().build());
    }

    @Test
    void sendsOpenAiRequestAndParsesTheJsonAnswer() {
        reply = """
                {"choices":[{"message":{"role":"assistant","content":"{\\"category\\":\\"INTERVIEW\\"}"}}]}""";

        JsonNode answer = client("secret-key").chatForJson("system text", "user text", SCHEMA);

        assertThat(answer.path("category").asString("")).isEqualTo("INTERVIEW");
        assertThat(lastAuth.get()).isEqualTo("Bearer secret-key");
        assertThat(lastBody.get()).contains("\"model\":\"test-model\"")
                .contains("\"json_schema\"")
                .contains("system text")
                .contains("user text");
    }

    @Test
    void fallsBackToPlainJsonModeWhenTheServiceRefusesASchema() {
        server.removeContext("/v1/chat/completions");
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody.set(request);
            boolean schemaMode = request.contains("json_schema");
            byte[] out = (schemaMode
                    ? "{\"error\":{\"message\":\"This response_format type is unavailable now\"}}"
                    : "{\"choices\":[{\"message\":{\"content\":\"{\\\"category\\\":\\\"OFFER\\\"}\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(schemaMode ? 400 : 200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        OllamaClient client = client("key");

        assertThat(client.chatForJson("system text", "user text", SCHEMA).path("category").asString(""))
                .isEqualTo("OFFER");
        assertThat(lastBody.get()).contains("json_object").contains("matching this JSON schema");
        // Remembered: the next call goes straight to plain JSON mode.
        lastBody.set(null);
        client.chatForJson("s", "u", SCHEMA);
        assertThat(lastBody.get()).doesNotContain("json_schema");
    }

    @Test
    void acceptsAnswersWrappedInCodeFences() {
        assertThat(OllamaClient.stripCodeFence("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(OllamaClient.stripCodeFence("{\"a\":1}")).isEqualTo("{\"a\":1}");
    }

    @Test
    void rejectedKeyBecomesAClearError() {
        status = 401;
        reply = "{\"error\":{\"message\":\"Invalid API Key\"}}";

        assertThatThrownBy(() -> client("wrong").chatForJson("s", "u", SCHEMA))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("rejected the API key");
    }

    @Test
    void missingKeyIsReportedWithoutCallingTheService() {
        assertThatThrownBy(() -> client("").chatForJson("s", "u", SCHEMA))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("no API key");
        assertThat(lastBody.get()).isNull();
    }

    @Test
    void ollamaStaysTheDefault() {
        AiProperties defaults = new AiProperties(null, null, null, null, null, null);
        assertThat(defaults.online()).isFalse();
        assertThat(defaults.model()).isEqualTo("qwen3:4b-instruct-2507-q4_K_M");
        assertThat(new AiProperties("openai", null, null, null, null, null).model()).isEqualTo("openai/gpt-oss-20b");
    }
}
