package com.jobtrack.gmail;

import com.jobtrack.ai.OllamaClient;
import com.jobtrack.dto.gmail.StatusChange;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the short summary shown after "Check for Updates". The facts are computed here, so the
 * model only has to phrase them; if the model is unavailable a plain summary is used instead.
 */
@Component
public class UpdateSummaryWriter {

    private static final Logger log = LoggerFactory.getLogger(UpdateSummaryWriter.class);

    private static final String SYSTEM_PROMPT = """
            You write a short, friendly update for a job seeker about their job applications.
            Use only the facts you are given. Never invent companies, dates or outcomes. Answer only with JSON.""";

    private final OllamaClient ollama;

    public UpdateSummaryWriter(OllamaClient ollama) {
        this.ollama = ollama;
    }

    public String write(List<JobApplication> applications, List<StatusChange> changes, int emailsRead, Instant now) {
        String facts = facts(applications, changes, emailsRead, now);
        try {
            String prompt = """
                    Facts:
                    %s

                    Write "summary": 2 or 3 short sentences (at most 60 words) telling the person what changed in this
                    check and where their job search stands. Mention rejections and interviews by company name.
                    Address the person as "you".""".formatted(facts);
            String summary = ollama.chatForJson(SYSTEM_PROMPT, prompt,
                    Map.of("type", "object",
                            "properties", Map.of("summary", Map.of("type", "string")),
                            "required", List.of("summary")))
                    .path("summary").asString("").strip();
            if (!summary.isEmpty()) {
                return summary.length() > 600 ? summary.substring(0, 600) + "…" : summary;
            }
        } catch (AiUnavailableException e) {
            log.info("Using the plain update summary: {}", e.getMessage());
        }
        return plainSummary(applications, changes, emailsRead, now);
    }

    /** The facts handed to the model, one per line. */
    static String facts(List<JobApplication> applications, List<StatusChange> changes, int emailsRead, Instant now) {
        StringBuilder facts = new StringBuilder();
        facts.append("- Job-related emails read in this check: ").append(emailsRead).append('\n');
        facts.append("- Applications tracked: ").append(applications.size())
                .append(" (").append(countsText(applications)).append(")\n");
        if (changes.isEmpty()) {
            facts.append("- No application changed status in this check.\n");
        }
        for (StatusChange change : changes) {
            facts.append("- ").append(change.companyName()).append(", ").append(change.jobTitle()).append(": ")
                    .append(change.from() == null
                            ? "new application found in the inbox, now " + label(change.to())
                            : label(change.from()) + " -> " + label(change.to()));
            if (change.emailSummary() != null) {
                facts.append(" (").append(change.emailSummary()).append(')');
            }
            facts.append('\n');
        }
        List<JobApplication> waiting = applications.stream().filter(a -> NoReplyRule.applies(a, now)).toList();
        if (!waiting.isEmpty()) {
            facts.append("- No reply for more than 14 days: ")
                    .append(waiting.stream().map(a -> a.getCompany().getName()).distinct().collect(Collectors.joining(", ")))
                    .append('\n');
        }
        return facts.toString().strip();
    }

    static String plainSummary(List<JobApplication> applications, List<StatusChange> changes, int emailsRead, Instant now) {
        StringBuilder text = new StringBuilder();
        if (changes.isEmpty()) {
            text.append(emailsRead == 0 ? "No new job emails since the last check. "
                    : "Read " + emailsRead + " job email" + (emailsRead == 1 ? "" : "s") + "; no status changed. ");
        } else {
            text.append(changes.stream()
                    .map(c -> c.from() == null
                            ? c.companyName() + " added (" + label(c.to()).toLowerCase(Locale.ROOT) + ")"
                            : c.companyName() + " moved to " + label(c.to()).toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining("; "))).append(". ");
        }
        if (!applications.isEmpty()) {
            text.append("You are tracking ").append(applications.size()).append(" application")
                    .append(applications.size() == 1 ? "" : "s").append(": ").append(countsText(applications)).append('.');
        }
        long waiting = applications.stream().filter(a -> NoReplyRule.applies(a, now)).count();
        if (waiting > 0) {
            text.append(' ').append(waiting).append(waiting == 1 ? " has" : " have").append(" had no reply for over two weeks.");
        }
        return text.toString().strip();
    }

    private static String countsText(List<JobApplication> applications) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        applications.forEach(a -> counts.merge(a.getStatus(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(e -> e.getValue() + " " + label(e.getKey()).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    static String label(ApplicationStatus status) {
        String name = status.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
