package com.jobtrack.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStatusTest {

    @ParameterizedTest
    @CsvSource({
            "SAVED, APPLIED",
            "APPLIED, SCREENING",
            "APPLIED, INTERVIEW",
            "SCREENING, INTERVIEW",
            "INTERVIEW, OFFER",
            "OFFER, ACCEPTED",
            "APPLIED, REJECTED",
            "INTERVIEW, WITHDRAWN",
            "OFFER, REJECTED"})
    void allowsForwardAndClosingTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "INTERVIEW, APPLIED",
            "OFFER, SCREENING",
            "SAVED, OFFER",
            "SAVED, REJECTED",
            "APPLIED, APPLIED",
            "APPLIED, ACCEPTED"})
    void rejectsBackwardsSkippedOrNoOpTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void finalStatusesAreClosedAndHaveNoTransitions(ApplicationStatus status) {
        assertThat(status.isClosed()).isTrue();
        assertThat(status.allowedTransitions()).isEmpty();
    }

    @Test
    void activeStatusesAreTheOpenPipelineStages() {
        assertThat(ApplicationStatus.ACTIVE).containsExactlyInAnyOrder(
                ApplicationStatus.APPLIED, ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    }

    @Test
    void allowedTransitionsIsADefensiveCopy() {
        ApplicationStatus.APPLIED.allowedTransitions().clear();
        assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.SCREENING)).isTrue();
    }
}
