package com.jobtrack.repository;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationSpecificationsTest {

    @Test
    void likePatternIsCaseInsensitiveContains() {
        assertThat(ApplicationSpecifications.likePattern("Backend")).isEqualTo("%backend%");
    }

    @Test
    void likePatternEscapesWildcardsInUserInput() {
        assertThat(ApplicationSpecifications.likePattern("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%");
    }
}
