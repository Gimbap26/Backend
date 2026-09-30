package com.moneyweather.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {
    /** 테스트에서 시간을 직접 앞으로 돌릴 수 있는 시계. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-29T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService attempts = new LoginAttemptService(3, 10, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterTheLimitAndReopensWhenOldFailuresLeaveTheWindow() {
        for (int i = 0; i < 3; i++) attempts.recordFailure("a@example.com", "1.1.1.1");

        assertThatThrownBy(() -> attempts.checkAllowed("a@example.com", "1.1.1.1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> attempts.checkAllowed("a@example.com", "1.1.1.1")).isInstanceOf(ResponseStatusException.class);

        clock.advance(Duration.ofMinutes(2));
        assertThatCode(() -> attempts.checkAllowed("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void emailIsComparedIgnoringCaseAndSpaces() {
        for (int i = 0; i < 3; i++) attempts.recordFailure("  A@Example.com ", "1.1.1.1");
        assertThatThrownBy(() -> attempts.checkAllowed("a@example.com", "2.2.2.2")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void successClearsOnlyThatEmail() {
        for (int i = 0; i < 3; i++) attempts.recordFailure("a@example.com", "1.1.1.1");
        attempts.recordSuccess("a@example.com");
        assertThatCode(() -> attempts.checkAllowed("a@example.com", "9.9.9.9")).doesNotThrowAnyException();
    }
}
