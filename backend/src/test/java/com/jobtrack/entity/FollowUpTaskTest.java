package com.jobtrack.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FollowUpTaskTest {

    private static final Instant T1 = Instant.parse("2026-09-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-02T10:00:00Z");

    @Test
    void completingRecordsTheCompletionTime() {
        FollowUpTask task = new FollowUpTask(null, "Send thank-you email");
        task.setCompleted(true, T1);
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getCompletedAt()).isEqualTo(T1);
    }

    @Test
    void completingAgainKeepsTheOriginalCompletionTime() {
        FollowUpTask task = new FollowUpTask(null, "Send thank-you email");
        task.setCompleted(true, T1);
        task.setCompleted(true, T2);
        assertThat(task.getCompletedAt()).isEqualTo(T1);
    }

    @Test
    void reopeningClearsTheCompletionTime() {
        FollowUpTask task = new FollowUpTask(null, "Send thank-you email");
        task.setCompleted(true, T1);
        task.setCompleted(false, T2);
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedAt()).isNull();
    }
}
