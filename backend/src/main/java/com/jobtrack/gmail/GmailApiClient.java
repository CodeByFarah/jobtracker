package com.jobtrack.gmail;

import com.jobtrack.config.GmailProperties;
import com.jobtrack.exception.GmailAuthException;
import org.jsoup.Jsoup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for Google's OAuth endpoints and the Gmail REST API (read-only scope), using
 * plain HTTP calls so no Google SDK is needed.
 */
@Component
public class GmailApiClient {

    public static final String SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final String API_URL = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final GmailProperties properties;

    public GmailApiClient(GmailProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    /** Google's consent page. {@code prompt=consent} makes Google return a refresh token every time. */
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    public Tokens exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");
        return parseTokens(postForm(form));
    }

    public Tokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "refresh_token");
        return parseTokens(postForm(form));
    }

    /** Tells Google to drop our access. Best effort: the caller ignores failures. */
    public void revoke(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        restClient.post().uri(REVOKE_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    public String emailAddress(String accessToken) {
        return get(accessToken, URI.create(API_URL + "/profile")).path("emailAddress").asString("");
    }

    /** Message ids matching a Gmail search query, newest first, at most {@code max}. */
    public List<String> listMessageIds(String accessToken, String query, int max) {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        do {
            JsonNode page = get(accessToken, messagesUri(query, Math.min(100, max - ids.size()), pageToken));
            for (JsonNode message : page.path("messages")) {
                ids.add(message.path("id").asString(""));
            }
            pageToken = page.path("nextPageToken").asString(null);
        } while (pageToken != null && ids.size() < max);
        return ids;
    }

    /**
     * The message-list URL. The search goes in as a URI variable so every character is encoded:
     * written straight into the template, Gmail's {a b c} "any of" syntax would be taken for a
     * template placeholder and break the URL.
     */
    static URI messagesUri(String query, int maxResults, String pageToken) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(API_URL + "/messages")
                .queryParam("q", "{q}")
                .queryParam("maxResults", maxResults);
        Map<String, Object> values = new HashMap<>();
        values.put("q", query);
        if (pageToken != null) {
            uri.queryParam("pageToken", "{pageToken}");
            values.put("pageToken", pageToken);
        }
        return uri.encode().buildAndExpand(values).toUri();
    }

    public GmailMessage getMessage(String accessToken, String id) {
        JsonNode message = get(accessToken, UriComponentsBuilder.fromUriString(API_URL + "/messages/{id}")
                .queryParam("format", "full")
                .buildAndExpand(id).encode().toUri());
        JsonNode payload = message.path("payload");
        String from = "";
        String subject = "";
        for (JsonNode header : payload.path("headers")) {
            String name = header.path("name").asString("");
            if (name.equalsIgnoreCase("From")) {
                from = header.path("value").asString("");
            } else if (name.equalsIgnoreCase("Subject")) {
                subject = header.path("value").asString("");
            }
        }
        StringBuilder plain = new StringBuilder();
        StringBuilder html = new StringBuilder();
        collectBodies(payload, plain, html, 0);
        String body = !plain.isEmpty() ? plain.toString() : Jsoup.parse(html.toString()).text();
        if (body.isBlank()) {
            body = message.path("snippet").asString("");
        }
        long internalDate = parseLong(message.path("internalDate").asString("0"));
        return new GmailMessage(id, from, subject, Instant.ofEpochMilli(internalDate), body.replaceAll("\\s+", " ").strip());
    }

    private static void collectBodies(JsonNode part, StringBuilder plain, StringBuilder html, int depth) {
        if (depth > 8) {
            return;
        }
        String mimeType = part.path("mimeType").asString("");
        String data = part.path("body").path("data").asString("");
        boolean attachment = !part.path("filename").asString("").isEmpty();
        if (!data.isEmpty() && !attachment) {
            String decoded = new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
            if (mimeType.equalsIgnoreCase("text/plain")) {
                plain.append(decoded).append('\n');
            } else if (mimeType.equalsIgnoreCase("text/html")) {
                html.append(decoded).append('\n');
            }
        }
        for (JsonNode child : part.path("parts")) {
            collectBodies(child, plain, html, depth + 1);
        }
    }

    private JsonNode get(String accessToken, URI uri) {
        try {
            String body = restClient.get().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(body == null ? "{}" : body);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new GmailAuthException("Gmail refused access. Please reconnect Gmail.", e);
        }
    }

    private JsonNode postForm(MultiValueMap<String, String> form) {
        try {
            String body = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return jsonMapper.readTree(body == null ? "{}" : body);
        } catch (HttpClientErrorException e) {
            String response = e.getResponseBodyAsString();
            if (response.contains("invalid_grant")) {
                throw new GmailAuthException(
                        "Gmail access has expired or was removed in your Google account. Please reconnect Gmail.", e);
            }
            if (response.contains("invalid_client") || response.contains("unauthorized_client")) {
                throw new GmailAuthException(
                        "Google rejected the app's credentials. Check GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env.", e);
            }
            throw new GmailAuthException("Google sign-in failed (HTTP " + e.getStatusCode().value() + ")", e);
        }
    }

    private static Tokens parseTokens(JsonNode json) {
        String accessToken = json.path("access_token").asString("");
        if (accessToken.isEmpty()) {
            throw new GmailAuthException("Google did not return an access token");
        }
        String refreshToken = json.path("refresh_token").asString("");
        return new Tokens(accessToken, refreshToken.isEmpty() ? null : refreshToken,
                json.path("expires_in").asLong(3600));
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
