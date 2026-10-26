package com.jobtrack.ai;

import com.jobtrack.dto.ai.JobLinkPreview;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.EmploymentType;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.service.ApplicationService;
import com.jobtrack.service.CompanyService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a job posting link into application details: fetch the page, read its schema.org
 * JobPosting data if present, and ask the local AI for whatever is still missing.
 */
@Service
public class JobLinkImportService {

    private static final int MAX_PAGE_BYTES = 3_000_000;
    private static final int MAX_TEXT_FOR_AI = 6_000;
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    private static final String SYSTEM_PROMPT = """
            You extract job posting details from the text of a web page. Answer only with JSON.
            Use an empty string, or 0 for numbers, when the page does not say.""";

    private final OllamaClient ollama;
    private final JsonMapper jsonMapper;
    private final CompanyService companyService;
    private final ApplicationService applicationService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public JobLinkImportService(OllamaClient ollama, JsonMapper jsonMapper, CompanyService companyService,
                                ApplicationService applicationService) {
        this.ollama = ollama;
        this.jsonMapper = jsonMapper;
        this.companyService = companyService;
        this.applicationService = applicationService;
    }

    public JobLinkPreview preview(Long userId, String url) {
        URI uri = validate(url);
        // Checked before reading the page: no point making the user wait for a job they already have.
        applicationService.findByJobUrl(userId, uri.toString()).ifPresent(existing -> {
            throw new BadRequestException("url", ApplicationService.alreadyAdded(existing));
        });
        Document document = Jsoup.parse(fetch(uri), uri.toString());

        ExtractedJob structured = JobPostingParser.fromStructuredData(document, jsonMapper);
        ExtractedJob result = structured;
        String source = "PAGE_DATA";
        if (structured == null || !structured.hasEssentials()) {
            ExtractedJob fromAi = extractWithAi(uri, document);
            result = structured == null ? fromAi : structured.orElse(fromAi);
            source = structured == null ? "AI" : "PAGE_DATA_AND_AI";
        }
        if (result.jobTitle() == null && result.companyName() == null) {
            throw new BadRequestException("url",
                    "Couldn't find a job posting on that page. Check the link, or try the job on the company's own careers page.");
        }

        String companyName = JobPostingParser.truncate(result.companyName(), 149);
        Long matchedCompanyId = companyName == null ? null
                : companyService.findSameCompany(userId, companyName).map(Company::getId).orElse(null);
        return new JobLinkPreview(
                uri.toString(),
                JobPostingParser.truncate(result.jobTitle(), 149),
                companyName,
                matchedCompanyId,
                JobPostingParser.truncate(result.location(), 149),
                result.employmentType(),
                result.salaryMin(),
                result.salaryMax(),
                result.salaryCurrency(),
                result.summary(),
                source);
    }

    private ExtractedJob extractWithAi(URI uri, Document document) {
        String pageText = JobPostingParser.pageText(document, MAX_TEXT_FOR_AI);
        if (pageText.isBlank()) {
            throw new BadRequestException("url", "That page has no readable text. It may need a login or JavaScript.");
        }
        String prompt = """
                Page URL: %s

                Page text:
                \"\"\"
                %s
                \"\"\"

                Extract:
                - jobTitle: the job title
                - companyName: the hiring company (not the job board or recruiting site)
                - location: city and country, or "Remote"
                - employmentType: FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, TEMPORARY or UNKNOWN
                - salaryMin, salaryMax: yearly salary numbers only if stated, otherwise 0
                - salaryCurrency: 3-letter currency code like USD or EUR if a salary is stated
                - summary: two short sentences about the role and its main requirements
                """.formatted(uri, pageText);

        JsonNode answer = ollama.chatForJson(SYSTEM_PROMPT, prompt, extractionSchema());
        Integer min = positive(answer.path("salaryMin"));
        Integer max = positive(answer.path("salaryMax"));
        if (min != null && max != null && min > max) {
            Integer swap = min;
            min = max;
            max = swap;
        }
        String currency = blankToNull(answer.path("salaryCurrency").asString(""));
        if (currency != null && !currency.matches("[A-Za-z]{3}")) {
            currency = null;
        }
        return new ExtractedJob(
                blankToNull(answer.path("jobTitle").asString("")),
                blankToNull(answer.path("companyName").asString("")),
                blankToNull(answer.path("location").asString("")),
                parseEmploymentType(answer.path("employmentType").asString("")),
                min,
                max,
                min == null && max == null || currency == null ? null : currency.toUpperCase(Locale.ROOT),
                blankToNull(answer.path("summary").asString("")));
    }

    private static Map<String, Object> extractionSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jobTitle", Map.of("type", "string"));
        properties.put("companyName", Map.of("type", "string"));
        properties.put("location", Map.of("type", "string"));
        properties.put("employmentType", Map.of("type", "string",
                "enum", List.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP", "TEMPORARY", "UNKNOWN")));
        properties.put("salaryMin", Map.of("type", "number"));
        properties.put("salaryMax", Map.of("type", "number"));
        properties.put("salaryCurrency", Map.of("type", "string"));
        properties.put("summary", Map.of("type", "string"));
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }

    /** Only public http(s) sites: never let a pasted link make the server call itself or the local network. */
    private static URI validate(String url) {
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw new BadRequestException("url", "That doesn't look like a valid link");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw new BadRequestException("url", "Use a full http(s) link, e.g. https://jobs.example.com/123");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw new BadRequestException("url", "Links to local or private network addresses aren't allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw new BadRequestException("url", "Couldn't find the website " + uri.getHost());
        }
        return uri;
    }

    private String fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() >= 400) {
                    throw new BadRequestException("url", "The website refused to show the page (HTTP "
                            + response.statusCode() + "). Some sites, like LinkedIn, block automatic reading: "
                            + "try the same job on the company's own careers page.");
                }
                byte[] bytes = body.readNBytes(MAX_PAGE_BYTES);
                return new String(bytes, charsetOf(response));
            }
        } catch (IOException e) {
            throw new BadRequestException("url", "Couldn't open that page: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("url", "Reading the page was interrupted");
        }
    }

    private static Charset charsetOf(HttpResponse<?> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        Matcher matcher = CHARSET.matcher(contentType);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (IllegalArgumentException ignored) {
                // unknown charset name: fall back to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static EmploymentType parseEmploymentType(String value) {
        try {
            return value.isBlank() || value.equals("UNKNOWN") ? null : EmploymentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer positive(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double value = node.doubleValue();
        return value >= 1 && value < Integer.MAX_VALUE ? (int) Math.round(value) : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
