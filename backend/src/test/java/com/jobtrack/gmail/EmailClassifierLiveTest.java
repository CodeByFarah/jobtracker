package com.jobtrack.gmail;

import com.jobtrack.ai.OllamaClient;
import com.jobtrack.config.AiProperties;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real local model (Ollama must be running) on realistic emails. Off by default because
 * it needs Ollama and takes a few minutes on a CPU:
 * <pre>mvnw test -Dtest=EmailClassifierLiveTest -Dollama.live=true</pre>
 */
@EnabledIfSystemProperty(named = "ollama.live", matches = "true")
class EmailClassifierLiveTest {

    private static final User USER = TestEntities.user(1);
    private static final JobApplication ACME_BACKEND = app(10, "Acme", "Backend Engineer");
    private static final JobApplication ACME_DATA = app(11, "Acme", "Data Engineer");
    private static final JobApplication GLOBEX = app(20, "Globex", "Product Designer");
    private static final JobApplication INITECH = app(30, "Initech", "Data Analyst");

    private final EmailClassifier classifier = new EmailClassifier(
            new OllamaClient(new AiProperties(null, null, null), JsonMapper.builder().build()));

    private static JobApplication app(long id, String company, String title) {
        JobApplication application = TestEntities.application(id, USER, TestEntities.company(id, USER, company),
                ApplicationStatus.APPLIED);
        ReflectionTestUtils.setField(application, "jobTitle", title);
        return application;
    }

    static Stream<Arguments> emails() {
        return Stream.of(
                Arguments.of("Rejection via an ATS",
                        "Acme Talent Team <no-reply@us.greenhouse-mail.io>", "Your application to Acme",
                        "Hi Sam, thank you for your interest in the Backend Engineer role at Acme and for the time you "
                                + "put into your application. After careful consideration, we have decided to move forward "
                                + "with other candidates whose experience more closely matches our needs. We wish you the best.",
                        List.of(ACME_BACKEND, GLOBEX), EmailCategory.REJECTED, 10L),
                Arguments.of("Interview invite",
                        "Maria Lopez <maria@globex.com>", "Globex - next steps",
                        "Hi Sam, thanks for applying for the Product Designer position. We were impressed by your portfolio "
                                + "and would love to set up a 30-minute video call with our design lead next week. Please pick "
                                + "a time that works for you here: https://calendly.com/globex/design-intro",
                        List.of(GLOBEX), EmailCategory.INTERVIEW, 20L),
                Arguments.of("Application received",
                        "Initech Careers <careers@initech.com>", "We received your application",
                        "Thank you for applying to Initech! This email confirms that we have received your application for "
                                + "Data Analyst. Our team will review it and get back to you if your profile matches.",
                        List.of(INITECH), EmailCategory.APPLICATION_RECEIVED, 30L),
                Arguments.of("Job alert mentioning a tracked company",
                        "LinkedIn Job Alerts <jobalerts-noreply@linkedin.com>", "30+ new jobs for 'engineer'",
                        "Jobs you may be interested in: Senior Backend Engineer at Acme - Istanbul (Remote). Staff Engineer "
                                + "at Globex - Berlin. Data Engineer at Wonka Industries. See all jobs. Unsubscribe.",
                        List.of(ACME_BACKEND, GLOBEX), EmailCategory.NOT_JOB_RELATED, null),
                Arguments.of("Online assessment",
                        "Globex Recruiting <recruiting@globex.com>", "Complete your Globex assessment",
                        "Hello Sam, as the next step for the Product Designer role, please complete the design challenge "
                                + "within the next 5 days. You will find the brief and the submission link below.",
                        List.of(GLOBEX), EmailCategory.ASSESSMENT, 20L),
                Arguments.of("Offer",
                        "Paul Smith <paul.smith@initech.com>", "Offer - Data Analyst",
                        "Dear Sam, we are delighted to offer you the position of Data Analyst at Initech, starting on "
                                + "1 December with an annual salary of EUR 52,000. The formal offer letter is attached; please "
                                + "let us know by Friday.",
                        List.of(INITECH), EmailCategory.OFFER, 30L),
                Arguments.of("Right application when two are at the same company",
                        "Acme Recruiting <jobs@acme.io>", "Update on your Data Engineer application",
                        "Hi Sam, unfortunately we won't be moving forward with your application for the Data Engineer "
                                + "position at this time. We'll keep your details on file for future roles.",
                        List.of(ACME_BACKEND, ACME_DATA), EmailCategory.REJECTED, 11L),
                Arguments.of("Polite 'position filled'",
                        "Initech HR <hr@initech.com>", "Data Analyst position",
                        "Dear Sam, thank you for your patience. We wanted to let you know that the Data Analyst position "
                                + "has now been filled. We appreciate your interest in Initech.",
                        List.of(INITECH), EmailCategory.REJECTED, 30L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emails")
    void classifiesRealisticEmails(String name, String from, String subject, String body,
                                   List<JobApplication> candidates, EmailCategory expectedCategory, Long expectedApplication) {
        long started = System.nanoTime();
        EmailClassification result = classifier.classify(
                new GmailMessage("m", from, subject, Instant.parse("2026-10-15T09:00:00Z"), body), candidates);
        System.out.printf("[live] %-50s -> %-20s app=%-4s conf=%.2f %5.1fs  %s%n", name, result.category(),
                result.applicationId(), result.confidence(), (System.nanoTime() - started) / 1e9, result.summary());

        assertThat(result.category()).isEqualTo(expectedCategory);
        assertThat(result.applicationId()).isEqualTo(expectedApplication);
    }

    static Stream<Arguments> untrackedEmails() {
        return Stream.of(
                Arguments.of("LinkedIn 'application sent'",
                        "LinkedIn <jobs-noreply@linkedin.com>", "Sam, your application was sent to Hooli",
                        "Your application was sent to Hooli. Senior Frontend Developer · Hooli · Istanbul (Hybrid). "
                                + "Applied on October 14. Good luck!",
                        EmailCategory.APPLICATION_RECEIVED, "hooli", "frontend"),
                Arguments.of("Rejection through Workday",
                        "Wonka Industries <wonka@myworkday.com>", "Your application status",
                        "Dear Sam, thank you for applying for the QA Engineer position at Wonka Industries. After "
                                + "reviewing your application, we have decided not to move forward at this time.",
                        EmailCategory.REJECTED, "wonka", "qa"),
                Arguments.of("Turkish interview invite",
                        "Trendyol İK <ik@trendyol.com>", "Mülakat Daveti - Backend Developer",
                        "Merhaba Sam, Backend Developer pozisyonu için yaptığınız başvuru değerlendirilmiştir. Sizi "
                                + "22 Ekim Çarşamba saat 14:00'te online bir görüşmeye davet etmek isteriz.",
                        EmailCategory.INTERVIEW, "trendyol", "backend"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("untrackedEmails")
    void namesTheCompanyAndJobOfUntrackedApplications(String name, String from, String subject, String body,
                                                      EmailCategory expectedCategory, String companyPart,
                                                      String titlePart) {
        long started = System.nanoTime();
        EmailClassification result = classifier.classify(
                new GmailMessage("m", from, subject, Instant.parse("2026-10-15T09:00:00Z"), body), List.of());
        System.out.printf("[live] %-50s -> %-20s company=%s title=%s conf=%.2f %5.1fs  %s%n", name,
                result.category(), result.companyName(), result.jobTitle(), result.confidence(),
                (System.nanoTime() - started) / 1e9, result.summary());

        assertThat(result.category()).isEqualTo(expectedCategory);
        assertThat(result.companyName()).containsIgnoringCase(companyPart);
        assertThat(result.jobTitle()).containsIgnoringCase(titlePart);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(EmailApplicationResolver.MIN_CONFIDENCE_TO_CREATE);
    }
}
