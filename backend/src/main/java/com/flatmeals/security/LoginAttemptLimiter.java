package com.flatmeals.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing once the site is on the internet.
 *
 * After 5 wrong passwords for the same username within 15 minutes, that
 * username can't sign in until the oldest of those 5 attempts is 15 minutes old.
 * A correct sign-in clears the count.
 *
 * The count is kept in memory, so restarting the app clears it. That is fine
 * for a flat: the goal is to make guessing thousands of passwords impossible,
 * not to keep a permanent record.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** How long this username must wait before trying again; Duration.ZERO if it may try now. */
    public Duration waitTime(String username) {
        Deque<Instant> recent = failures.get(key(username));
        if (recent == null) {
            return Duration.ZERO;
        }
        synchronized (recent) {
            dropOld(recent);
            if (recent.size() < MAX_FAILURES) {
                return Duration.ZERO;
            }
            return Duration.between(clock.instant(), recent.peekFirst().plus(WINDOW));
        }
    }

    public void recordFailure(String username) {
        Deque<Instant> recent = failures.computeIfAbsent(key(username), k -> new ArrayDeque<>());
        synchronized (recent) {
            dropOld(recent);
            recent.addLast(clock.instant());
            while (recent.size() > MAX_FAILURES) {
                recent.removeFirst();
            }
        }
        forgetStaleUsernames();
    }

    public void recordSuccess(String username) {
        failures.remove(key(username));
    }

    private void dropOld(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.removeFirst();
        }
    }

    /** Keeps memory small if someone tries thousands of made-up usernames. */
    private void forgetStaleUsernames() {
        if (failures.size() < 1000) {
            return;
        }
        failures.values().removeIf(recent -> {
            synchronized (recent) {
                dropOld(recent);
                return recent.isEmpty();
            }
        });
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
