package com.jobtrack.ai;

import com.jobtrack.entity.EmploymentType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads job details from a posting page without AI. Most job boards (Greenhouse, Lever, Workable,
 * Indeed, LinkedIn, company career sites...) embed a schema.org {@code JobPosting} object for
 * Google Jobs, which is exact and free to read. The AI is only needed when it is missing.
 */
public final class JobPostingParser {

    private static final int SUMMARY_CHARS = 600;

    private JobPostingParser() {
    }

    /** The JobPosting embedded as JSON-LD, or null if the page has none. */
    public static ExtractedJob fromStructuredData(Document document, JsonMapper jsonMapper) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            JsonNode root;
            try {
                root = jsonMapper.readTree(script.data());
            } catch (JacksonException e) {
                continue; // some sites ship invalid JSON-LD; just try the next block
            }
            JsonNode posting = findJobPosting(root, 0);
            if (posting != null) {
                return toExtractedJob(posting);
            }
        }
        return null;
    }

    /** Readable text of the page (title, meta description, body), for the AI. */
    public static String pageText(Document document, int maxChars) {
        Document copy = document.clone();
        StringBuilder text = new StringBuilder();
        appendLine(text, copy.title());
        appendLine(text, copy.select("meta[property=og:title]").attr("content"));
        appendLine(text, copy.select("meta[property=og:site_name]").attr("content"));
        appendLine(text, copy.select("meta[name=description]").attr("content"));
        copy.select("script, style, noscript, svg, nav, footer, iframe, form").remove();
        if (copy.body() != null) {
            appendLine(text, copy.body().text());
        }
        return truncate(text.toString().strip(), maxChars);
    }

    private static JsonNode findJobPosting(JsonNode node, int depth) {
        if (node == null || depth > 5) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode found = findJobPosting(item, depth + 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        if (isJobPostingType(node.get("@type"))) {
            return node;
        }
        return findJobPosting(node.get("@graph"), depth + 1);
    }

    private static boolean isJobPostingType(JsonNode type) {
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("JobPosting".equalsIgnoreCase(t.asString(""))) {
                    return true;
                }
            }
            return false;
        }
        return "JobPosting".equalsIgnoreCase(type.asString(""));
    }

    private static ExtractedJob toExtractedJob(JsonNode posting) {
        String title = cleanHtml(text(posting.get("title")));
        JsonNode organization = posting.get("hiringOrganization");
        String company = organization != null && organization.isObject()
                ? cleanHtml(text(organization.get("name")))
                : cleanHtml(text(organization));

        Integer salaryMin = null;
        Integer salaryMax = null;
        String currency = null;
        JsonNode salary = posting.get("baseSalary");
        if (salary != null && salary.isObject()) {
            JsonNode value = salary.get("value");
            String unit = value != null && value.isObject() ? text(value.get("unitText")) : text(salary.get("unitText"));
            // Only yearly (or unspecified) amounts fit the salary fields; hourly rates would mislead.
            if (unit == null || unit.equalsIgnoreCase("YEAR")) {
                if (value != null && value.isObject()) {
                    salaryMin = number(value.get("minValue"));
                    salaryMax = number(value.get("maxValue"));
                    if (salaryMin == null && salaryMax == null) {
                        salaryMin = number(value.get("value"));
                        salaryMax = salaryMin;
                    }
                } else {
                    salaryMin = number(value);
                    salaryMax = salaryMin;
                }
                currency = text(salary.get("currency"));
            }
        }
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            Integer swap = salaryMin;
            salaryMin = salaryMax;
            salaryMax = swap;
        }
        if (currency != null && !currency.matches("[A-Za-z]{3}")) {
            currency = null;
        }

        String description = cleanHtml(text(posting.get("description")));
        return new ExtractedJob(
                title,
                company,
                location(posting),
                employmentType(posting.get("employmentType")),
                salaryMin,
                salaryMax,
                currency == null ? null : currency.toUpperCase(Locale.ROOT),
                description == null ? null : truncate(description, SUMMARY_CHARS));
    }

    private static String location(JsonNode posting) {
        Set<String> places = new LinkedHashSet<>();
        JsonNode jobLocation = posting.get("jobLocation");
        List<JsonNode> locations = new ArrayList<>();
        if (jobLocation != null && jobLocation.isArray()) {
            jobLocation.forEach(locations::add);
        } else if (jobLocation != null) {
            locations.add(jobLocation);
        }
        for (JsonNode place : locations) {
            JsonNode address = place.isObject() ? place.get("address") : place;
            if (address == null) {
                continue;
            }
            if (address.isObject()) {
                List<String> parts = new ArrayList<>();
                addIfPresent(parts, text(address.get("addressLocality")));
                addIfPresent(parts, text(address.get("addressRegion")));
                JsonNode country = address.get("addressCountry");
                addIfPresent(parts, country != null && country.isObject() ? text(country.get("name")) : text(country));
                if (!parts.isEmpty()) {
                    places.add(String.join(", ", parts.stream().distinct().toList()));
                }
            } else {
                addIfPresent(places, text(address));
            }
            if (places.size() >= 3) {
                break;
            }
        }
        boolean remote = "TELECOMMUTE".equalsIgnoreCase(text(posting.get("jobLocationType")));
        String joined = places.isEmpty() ? null : String.join(" / ", places);
        if (remote) {
            return joined == null ? "Remote" : "Remote (" + joined + ")";
        }
        return joined == null ? null : truncate(joined, 150);
    }

    static EmploymentType employmentType(JsonNode node) {
        if (node == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(v -> values.add(v.asString("")));
        } else {
            values.add(node.asString(""));
        }
        for (String value : values) {
            String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
            switch (normalized) {
                case "FULLTIME", "PERMANENT" -> {
                    return EmploymentType.FULL_TIME;
                }
                case "PARTTIME" -> {
                    return EmploymentType.PART_TIME;
                }
                case "CONTRACTOR", "CONTRACT", "FREELANCE" -> {
                    return EmploymentType.CONTRACT;
                }
                case "INTERN", "INTERNSHIP" -> {
                    return EmploymentType.INTERNSHIP;
                }
                case "TEMPORARY", "SEASONAL" -> {
                    return EmploymentType.TEMPORARY;
                }
                default -> {
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isContainer()) {
            return null;
        }
        String value = node.asString("").strip();
        return value.isEmpty() ? null : value;
    }

    private static Integer number(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            double value = node.doubleValue();
            return value > 0 && value < Integer.MAX_VALUE ? (int) Math.round(value) : null;
        }
        String digits = node.asString("").replaceAll("[^0-9.]", "");
        try {
            double value = Double.parseDouble(digits);
            return value > 0 && value < Integer.MAX_VALUE ? (int) Math.round(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Descriptions are often HTML (and sometimes HTML-escaped twice), so parse it to plain text. */
    private static String cleanHtml(String value) {
        if (value == null) {
            return null;
        }
        String text = Jsoup.parse(value).text();
        if (text.contains("<") && text.contains(">")) {
            text = Jsoup.parse(text).text();
        }
        text = text.strip();
        return text.isEmpty() ? null : text;
    }

    private static void addIfPresent(java.util.Collection<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.strip());
        }
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (line != null && !line.isBlank()) {
            builder.append(line.strip()).append('\n');
        }
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max).strip() + "…";
    }
}
