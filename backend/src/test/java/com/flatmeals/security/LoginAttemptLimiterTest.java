package com.flatmeals.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    /** A clock the test can move forward by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(username);
            clock.advance(Duration.ofMinutes(1));
        }
    }

    @Test
    void fourMistakesAreFine() {
        fail("anila", 4);
        assertThat(limiter.waitTime("anila")).isZero();
    }

    @Test
    void theFifthMistakeLocksUntilTheFirstIs15MinutesOld() {
        fail("anila", 5);                       // at 10:00 .. 10:04, now 10:05
        assertThat(limiter.waitTime("anila")).isEqualTo(Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(10));  // 10:15: the 10:00 mistake has expired
        assertThat(limiter.waitTime("anila")).isZero();
    }

    @Test
    void usernamesAreCountedSeparatelyIgnoringCase() {
        fail("Anila", 5);
        assertThat(limiter.waitTime("anila ")).isPositive();
        assertThat(limiter.waitTime("srijila")).isZero();
    }

    @Test
    void aCorrectPasswordClearsTheCount() {
        fail("anila", 4);
        limiter.recordSuccess("anila");
        fail("anila", 4);
        assertThat(limiter.waitTime("anila")).isZero();
    }
}
