package com.jobtrack.gmail;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.EmailCategory;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** How the model's JSON answer is interpreted, including the mistakes a small model makes. */
class EmailClassifierParseTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final User user = TestEntities.user(1);
    private final JobApplication acme = TestEntities.application(10, user, TestEntities.company(1, user, "Acme"), ApplicationStatus.APPLIED);
    private final JobApplication globex = TestEntities.application(20, user, TestEntities.company(2, user, "Globex"), ApplicationStatus.APPLIED);

    private EmailClassification parse(String json, List<JobApplication> candidates) {
        return EmailClassifier.parse(jsonMapper.readTree(json), candidates);
    }

    @Test
    void readsAWellFormedAnswer() {
        EmailClassification result = parse("""
                {"summary":"Acme declined.","applicationId":10,"category":"REJECTED","confidence":0.92}""",
                List.of(acme, globex));

        assertThat(result.applicationId()).isEqualTo(10L);
        assertThat(result.category()).isEqualTo(EmailCategory.REJECTED);
        assertThat(result.confidence()).isEqualTo(0.92);
        assertThat(result.summary()).isEqualTo("Acme declined.");
    }

    @Test
    void anIdThatIsNotOneOfTheCandidatesIsIgnored() {
        EmailClassification result = parse("""
                {"summary":"x","applicationId":999,"category":"INTERVIEW","confidence":0.9}""", List.of(acme, globex));

        assertThat(result.applicationId()).isNull();
    }

    @Test
    void aSingleCandidateIsUsedWhenTheModelForgetsTheId() {
        EmailClassification result = parse("""
                {"summary":"x","applicationId":0,"category":"INTERVIEW","confidence":0.8}""", List.of(acme));

        assertThat(result.applicationId()).isEqualTo(10L);
    }

    @Test
    void notJobRelatedEmailsAreNeverLinkedToAnApplication() {
        EmailClassification result = parse("""
                {"summary":"Job alert","applicationId":10,"category":"NOT_JOB_RELATED","confidence":0.9}""", List.of(acme));

        assertThat(result.applicationId()).isNull();
    }

    @Test
    void percentagesAndUnknownCategoriesAreNormalised() {
        EmailClassification result = parse("""
                {"summary":"","applicationId":10,"category":"MAYBE","confidence":85}""", List.of(acme));

        assertThat(result.category()).isEqualTo(EmailCategory.OTHER);
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.summary()).isNull();
    }
}
