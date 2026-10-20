package com.jobtrack.gmail;

import com.jobtrack.ai.OllamaClient;
import com.jobtrack.entity.EmailCategory;
import com.jobtrack.entity.JobApplication;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Asks the local AI what an email means for the user's applications. */
@Component
public class EmailClassifier {

    private static final int MAX_BODY_CHARS = 3_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private static final String SYSTEM_PROMPT = """
            You help a job seeker keep track of their job applications by reading the emails they receive.
            You decide whether an email is about one of the person's own job applications, which company
            and job it is about, and what it means. Emails can be in any language. Be careful:
            - Automatic job alerts, "jobs you may like", recommended jobs, newsletters and marketing are
              NOT_JOB_RELATED, even when they mention a company or a job.
            - The company is the employer that is hiring, not the job board or recruiting software
              (not LinkedIn, Indeed, Greenhouse, Workday, Lever, Kariyer.net and so on).
            - Only use REJECTED when the email clearly says the company will not move forward with this person.
            - Only use INTERVIEW when a real interview or call is being offered, scheduled or confirmed.
            Answer only with JSON.""";

    private final OllamaClient ollama;

    public EmailClassifier(OllamaClient ollama) {
        this.ollama = ollama;
    }

    public EmailClassification classify(GmailMessage message, List<JobApplication> candidates) {
        String applications = candidates.isEmpty() ? "(none of them match this email)" : candidates.stream()
                .map(a -> "[" + a.getId() + "] " + a.getJobTitle() + " at " + a.getCompany().getName()
                        + " (current status: " + a.getStatus() + ")")
                .collect(Collectors.joining("\n"));
        String body = message.body() == null ? "" : message.body();
        if (body.length() > MAX_BODY_CHARS) {
            body = body.substring(0, MAX_BODY_CHARS) + " …";
        }
        String prompt = """
                The person's tracked job applications:
                %s

                Email:
                From: %s
                Subject: %s
                Date: %s
                Body:
                \"\"\"
                %s
                \"\"\"

                Return:
                - summary: one short sentence in English saying what this email tells the person
                - applicationId: the number in [brackets] of the tracked application this email is about,
                  or 0 if it is about none of them (for example an application they haven't tracked yet)
                - company: the name of the hiring company this email is about, or "" if none
                - jobTitle: the job title this email is about, or "" if it doesn't say
                - category: one of
                  APPLICATION_RECEIVED (confirms the application was received or submitted),
                  INTERVIEW (invites to, schedules or confirms an interview or call),
                  ASSESSMENT (asks to complete a test, assignment, or online assessment),
                  OFFER (makes a job offer),
                  REJECTED (they are not moving forward / position filled / not selected),
                  OTHER (about the application but none of the above),
                  NOT_JOB_RELATED (job alert, newsletter, marketing, or not about the person's own application)
                - confidence: a number from 0 to 1
                """.formatted(applications, message.from(), message.subject(),
                DATE.format(message.receivedAt()), body);

        JsonNode answer = ollama.chatForJson(SYSTEM_PROMPT, prompt, schema());
        return parse(answer, candidates);
    }

    static EmailClassification parse(JsonNode answer, List<JobApplication> candidates) {
        EmailCategory category;
        try {
            category = EmailCategory.valueOf(answer.path("category").asString("OTHER"));
        } catch (IllegalArgumentException e) {
            category = EmailCategory.OTHER;
        }
        long id = answer.path("applicationId").isNumber() ? answer.path("applicationId").longValue() : 0;
        Long applicationId = candidates.stream().anyMatch(a -> a.getId() == id) ? id : null;
        // A small model sometimes forgets the id; with a single candidate the match is unambiguous.
        if (applicationId == null && candidates.size() == 1 && category != EmailCategory.NOT_JOB_RELATED) {
            applicationId = candidates.get(0).getId();
        }
        if (category == EmailCategory.NOT_JOB_RELATED) {
            applicationId = null;
        }
        double confidence = answer.path("confidence").isNumber() ? answer.path("confidence").doubleValue() : 0.5;
        if (confidence > 1 && confidence <= 100) {
            confidence = confidence / 100; // "85" meaning 85%
        }
        confidence = Math.max(0, Math.min(1, confidence));
        String summary = answer.path("summary").asString("").strip();
        if (summary.length() > 300) {
            summary = summary.substring(0, 300) + "…";
        }
        return new EmailClassification(applicationId, category, confidence, summary.isEmpty() ? null : summary,
                text(answer, "company", 150), text(answer, "jobTitle", 150));
    }

    /** A short text field, or null when the model left it empty or wrote a placeholder. */
    private static String text(JsonNode answer, String field, int max) {
        String value = answer.path(field).asString("").strip();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty() || lower.equals("unknown") || lower.equals("none") || lower.equals("n/a")
                || lower.equals("null")) {
            return null;
        }
        return value.length() > max ? value.substring(0, max).strip() : value;
    }

    private static Map<String, Object> schema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        // Summary first: writing it down helps a small model before it commits to a category.
        properties.put("summary", Map.of("type", "string"));
        properties.put("applicationId", Map.of("type", "integer"));
        properties.put("company", Map.of("type", "string"));
        properties.put("jobTitle", Map.of("type", "string"));
        properties.put("category", Map.of("type", "string",
                "enum", Arrays.stream(EmailCategory.values()).map(Enum::name).toList()));
        properties.put("confidence", Map.of("type", "number"));
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }
}
