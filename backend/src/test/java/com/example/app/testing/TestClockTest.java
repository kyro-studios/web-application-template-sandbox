package com.example.app.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TestClockTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void advancesTime() {
        TestClock clock = new TestClock(START);

        clock.advance(Duration.ofMinutes(29).plusSeconds(59));

        assertEquals(Instant.parse("2026-01-01T00:29:59Z"), clock.instant());
    }

    @Test
    void setsTime() {
        TestClock clock = new TestClock(START);

        clock.set(Instant.parse("2026-02-01T00:00:00Z"));

        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), clock.instant());
    }

    @Test
    void clockWithOtherZoneSharesTheTime() {
        TestClock clock = new TestClock(START);
        Clock tokyo = clock.withZone(ZoneId.of("Asia/Tokyo"));

        clock.advance(Duration.ofMinutes(15));

        assertEquals(clock.instant(), tokyo.instant());
        assertEquals(ZoneId.of("Asia/Tokyo"), tokyo.getZone());
    }
}
