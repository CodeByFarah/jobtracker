package com.jobtrack.ai;

import com.jobtrack.entity.EmploymentType;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingParserTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void readsSchemaOrgJobPosting() {
        String html = """
                <html><head>
                <script type="application/ld+json">{"@context":"https://schema.org","@type":"Organization","name":"Board"}</script>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@type":"JobPosting",
                 "title":"Senior Backend Engineer &amp; Mentor",
                 "hiringOrganization":{"@type":"Organization","name":"Acme Corp"},
                 "employmentType":["FULL_TIME"],
                 "jobLocation":{"@type":"Place","address":{"addressLocality":"Istanbul","addressCountry":"TR"}},
                 "baseSalary":{"@type":"MonetaryAmount","currency":"eur",
                   "value":{"@type":"QuantitativeValue","minValue":"70000","maxValue":90000,"unitText":"YEAR"}},
                 "description":"&lt;p&gt;Build &lt;b&gt;APIs&lt;/b&gt; in Java.&lt;/p&gt;"}
                </script></head><body>Ignored</body></html>
                """;

        ExtractedJob job = JobPostingParser.fromStructuredData(Jsoup.parse(html), jsonMapper);

        assertThat(job).isNotNull();
        assertThat(job.jobTitle()).isEqualTo("Senior Backend Engineer & Mentor");
        assertThat(job.companyName()).isEqualTo("Acme Corp");
        assertThat(job.location()).isEqualTo("Istanbul, TR");
        assertThat(job.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(job.salaryMin()).isEqualTo(70000);
        assertThat(job.salaryMax()).isEqualTo(90000);
        assertThat(job.salaryCurrency()).isEqualTo("EUR");
        assertThat(job.summary()).isEqualTo("Build APIs in Java.");
        assertThat(job.hasEssentials()).isTrue();
    }

    @Test
    void findsPostingInsideAGraphAndHandlesRemoteAndHourlyPay() {
        String html = """
                <script type="application/ld+json">
                {"@graph":[{"@type":"WebPage"},{"@type":["JobPosting"],"title":"Support Agent",
                  "hiringOrganization":"Globex","jobLocationType":"TELECOMMUTE","employmentType":"CONTRACTOR",
                  "baseSalary":{"currency":"USD","value":{"value":25,"unitText":"HOUR"}}}]}
                </script>
                """;

        ExtractedJob job = JobPostingParser.fromStructuredData(Jsoup.parse(html), jsonMapper);

        assertThat(job.companyName()).isEqualTo("Globex");
        assertThat(job.location()).isEqualTo("Remote");
        assertThat(job.employmentType()).isEqualTo(EmploymentType.CONTRACT);
        // Hourly pay doesn't fit the yearly salary fields, so it is left out.
        assertThat(job.salaryMin()).isNull();
        assertThat(job.salaryCurrency()).isNull();
    }

    @Test
    void pageWithoutStructuredDataReturnsNullButHasReadableText() {
        var document = Jsoup.parse("<html><head><title>Data Analyst - Initech</title><script>var x=1;</script></head>"
                + "<body><nav>Menu</nav><h1>Data Analyst</h1><p>Join our team.</p></body></html>");

        assertThat(JobPostingParser.fromStructuredData(document, jsonMapper)).isNull();
        assertThat(JobPostingParser.pageText(document, 1000))
                .contains("Data Analyst - Initech", "Join our team.")
                .doesNotContain("var x", "Menu");
    }

    @Test
    void structuredValuesWinAndGapsAreFilledFromTheAi() {
        ExtractedJob page = new ExtractedJob("Engineer", null, null, null, null, null, null, null);
        ExtractedJob ai = new ExtractedJob("Something else", "Acme", "Berlin", EmploymentType.FULL_TIME, 1, 2, "EUR", "Sum");

        ExtractedJob merged = page.orElse(ai);

        assertThat(merged.jobTitle()).isEqualTo("Engineer");
        assertThat(merged.companyName()).isEqualTo("Acme");
        assertThat(merged.salaryMax()).isEqualTo(2);
        assertThat(merged.summary()).isEqualTo("Sum");
    }
}
