package com.jobtrack.gmail;

import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;

import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheap pre-filter that runs before the AI: an email is only worth reading if it mentions the
 * company of an open application (in the sender, subject or body) or comes from the company's
 * domain. This keeps the slow local model away from the hundreds of unrelated emails in an inbox.
 */
public final class CompanyMatcher {

    private static final int BODY_CHARS = 6_000;
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@([\\w-]+(?:\\.[\\w-]+)+)");
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "inc", "incorporated", "ltd", "limited", "llc", "llp", "plc", "gmbh", "ag", "sa", "sas", "bv", "nv",
            "co", "corp", "corporation", "company", "as", "a s", "sti", "ltd sti", "srl", "spa", "oy", "ab", "pty");

    private CompanyMatcher() {
    }

    /** The open applications this email might be about (often none). */
    public static List<JobApplication> candidates(GmailMessage message, List<JobApplication> openApplications) {
        String body = message.body() == null ? "" : message.body();
        String haystack = " " + normalize(message.from() + " " + message.subject() + " "
                + body.substring(0, Math.min(body.length(), BODY_CHARS))) + " ";
        String senderDomain = senderDomain(message.from());
        return openApplications.stream()
                .filter(application -> matches(application.getCompany(), haystack, senderDomain))
                .toList();
    }

    static boolean matches(Company company, String haystack, String senderDomain) {
        String name = coreName(company.getName());
        if (name.length() >= 2 && haystack.contains(" " + name + " ")) {
            return true;
        }
        if (senderDomain != null) {
            String compact = name.replace(" ", "");
            if (compact.length() >= 4 && senderDomain.contains(compact)) {
                return true; // e.g. "Deluxe Time Hire" and careers@deluxetimehire.com
            }
            String website = websiteDomain(company.getWebsite());
            if (website != null && (senderDomain.equals(website) || senderDomain.endsWith("." + website))) {
                return true;
            }
        }
        return false;
    }

    /** "Acme Corp, Inc." -> "acme": lower case, accents and punctuation removed, legal suffixes dropped. */
    public static String coreName(String name) {
        String normalized = normalize(name);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : LEGAL_SUFFIXES) {
                if (normalized.endsWith(" " + suffix) && normalized.length() > suffix.length() + 2) {
                    normalized = normalized.substring(0, normalized.length() - suffix.length() - 1).strip();
                    changed = true;
                }
            }
        }
        return normalized;
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replace('ı', 'i') // Turkish dotless i
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }

    public static String senderDomain(String from) {
        if (from == null) {
            return null;
        }
        Matcher matcher = EMAIL.matcher(from);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static String websiteDomain(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(website.strip()).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
