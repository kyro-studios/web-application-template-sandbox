package com.example.app.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 試験で時刻を進められる時計。
 */
public final class TestClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public TestClock(Instant start) {
        this(new AtomicReference<>(Objects.requireNonNull(start)), ZoneOffset.UTC);
    }

    private TestClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    /** 同じ時刻を共有し、タイムゾーンだけが違う時計を返す。 */
    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }

    /** 時刻を進める。 */
    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    /** 時刻を指定した値にする。 */
    public void set(Instant newInstant) {
        instant.set(Objects.requireNonNull(newInstant));
    }
}
