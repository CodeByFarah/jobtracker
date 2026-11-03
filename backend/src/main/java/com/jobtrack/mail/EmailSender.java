package com.jobtrack.mail;

import com.jobtrack.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Sends plain transactional emails through the configured provider (see {@link MailProperties}). */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final MailProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public EmailSender(MailProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * Sends one email.
     *
     * @throws EmailFailedException if the provider refused it or couldn't be reached
     */
    public void send(String to, String subject, String text, String html) {
        if (!enabled()) {
            throw new EmailFailedException("Email isn't set up on this server", null);
        }
        if (properties.provider().equals(MailProperties.LOG)) {
            log.info("Email (log mode, not sent) to {} | {}\n{}", to, subject, text);
            return;
        }
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", properties.fromEmail(), "name", properties.fromName()),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text,
                "htmlContent", html);
        try {
            restClient.post().uri(BREVO_URL)
                    .header("api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EmailFailedException("The email service didn't accept the email: " + e.getMessage(), e);
        }
    }

    public static class EmailFailedException extends RuntimeException {
        public EmailFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
