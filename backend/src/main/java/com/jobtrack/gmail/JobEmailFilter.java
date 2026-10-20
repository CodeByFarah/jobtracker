package com.jobtrack.gmail;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Recognises emails that could be about one of the user's own job applications, from any company,
 * before the slower AI reads them. It is deliberately generous: anything that looks like hiring
 * mail passes, and the AI then decides (job alerts and newsletters come back NOT_JOB_RELATED).
 * <p>
 * The same terms build the Gmail search, so Gmail only returns candidate emails and the per-check
 * limit is spent on job mail rather than on promotions.
 */
public final class JobEmailFilter {

    /**
     * Applicant tracking systems that send application emails on employers' behalf. Job boards
     * (LinkedIn, Indeed, Kariyer.net…) are not listed: most of their mail is alerts, and their
     * application emails ("Your application was sent to…") are caught by the wording instead.
     */
    static final List<String> HIRING_DOMAINS = List.of(
            "greenhouse.io", "greenhouse-mail.io", "lever.co", "myworkday.com", "workday.com",
            "smartrecruiters.com", "ashbyhq.com", "icims.com", "taleo.net", "successfactors.com",
            "successfactors.eu", "jobvite.com", "bamboohr.com", "workable.com", "recruitee.com", "teamtailor.com",
            "personio.de", "personio.com", "breezy.hr", "pinpointhq.com", "jazzhr.com", "applytojob.com",
            "dover.com", "eightfold.ai", "hirevue.com", "codility.com", "hackerrank.com", "codesignal.com",
            "testgorilla.com");

    /** Words and phrases that show up in application emails (English and Turkish), matched as whole words. */
    static final List<String> TERMS = List.of(
            "application", "applications", "applied", "applying", "applicant", "candidate", "candidacy",
            "interview", "interviews", "interviewing", "recruiter", "recruiting", "recruitment", "hiring",
            "talent acquisition", "job offer", "offer letter", "offer of employment", "assessment", "coding challenge",
            "take home", "technical test", "thank you for your interest", "not to move forward", "position",
            "vacancy",
            "başvuru", "başvurunuz", "başvurusu", "başvurduğunuz", "mülakat", "görüşme", "aday", "adaylık",
            "pozisyon", "işe alım", "iş teklifi", "teklif mektubu", "değerlendirme");

    /** Left out of the Gmail search: plural forms Gmail already matches, and very common words. */
    private static final Set<String> SEARCH_SKIP = Set.of("position", "aday", "applications", "interviews");

    private JobEmailFilter() {
    }

    /** True when the email might be about a job application and is worth the AI's time. */
    public static boolean looksJobRelated(GmailMessage message) {
        String sender = CompanyMatcher.senderDomain(message.from());
        if (sender != null && HIRING_DOMAINS.stream().anyMatch(d -> sender.equals(d) || sender.endsWith("." + d))) {
            return true;
        }
        String body = message.body() == null ? "" : message.body();
        String text = " " + CompanyMatcher.normalize(message.subject() + " "
                + body.substring(0, Math.min(body.length(), 6_000))) + " ";
        return TERMS.stream().anyMatch(term -> text.contains(" " + CompanyMatcher.normalize(term) + " "));
    }

    /**
     * Gmail search clause selecting likely job mail: hiring senders, application wording, or the name
     * of a company the user already tracks.
     */
    public static String searchClause(List<String> trackedCompanyNames) {
        Stream<String> senders = HIRING_DOMAINS.stream().map(d -> "from:" + d);
        Stream<String> words = TERMS.stream().filter(t -> !SEARCH_SKIP.contains(t)).map(JobEmailFilter::quote);
        Stream<String> companies = trackedCompanyNames.stream()
                .map(name -> name.replace("\"", "").strip())
                .filter(name -> name.length() >= 2)
                .distinct()
                .map(name -> "\"" + name + "\"");
        return Stream.of(senders, words, companies).flatMap(s -> s).collect(Collectors.joining(" ", "{", "}"));
    }

    private static String quote(String term) {
        return term.contains(" ") ? "\"" + term + "\"" : term;
    }
}
