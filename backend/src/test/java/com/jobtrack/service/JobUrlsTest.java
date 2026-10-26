package com.jobtrack.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobUrlsTest {

    @Test
    void theSamePostingSharedFromDifferentPlacesIsOneLink() {
        String plain = JobUrls.normalize("https://jobs.lever.co/acme/123");
        assertThat(JobUrls.normalize("https://jobs.lever.co/acme/123/")).isEqualTo(plain);
        assertThat(JobUrls.normalize("https://JOBS.lever.co/acme/123?lever-source=LinkedIn")).isEqualTo(plain);
        assertThat(JobUrls.normalize("https://jobs.lever.co/acme/123?utm_source=x&utm_medium=y#apply")).isEqualTo(plain);
        assertThat(JobUrls.normalize("https://www.linkedin.com/jobs/view/42/?trk=feed&refId=abc"))
                .isEqualTo(JobUrls.normalize("https://linkedin.com/jobs/view/42"));
    }

    @Test
    void differentPostingsStayDifferent() {
        assertThat(JobUrls.normalize("https://boards.greenhouse.io/acme/jobs/1"))
                .isNotEqualTo(JobUrls.normalize("https://boards.greenhouse.io/acme/jobs/2"));
        // A query parameter that identifies the job is kept.
        assertThat(JobUrls.normalize("https://acme.com/careers?gh_jid=1"))
                .isNotEqualTo(JobUrls.normalize("https://acme.com/careers?gh_jid=2"));
    }

    @Test
    void blankIsNoLink() {
        assertThat(JobUrls.normalize(null)).isNull();
        assertThat(JobUrls.normalize("  ")).isNull();
    }
}
