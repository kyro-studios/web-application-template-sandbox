package com.example.app.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 試験で時刻を進められる時計。自分では進まず、{@link #advance(Duration)} と {@link #set(Instant)} でだけ変わる。 */
public final class MutableClock extends Clock {
    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** 2026-01-01T00:00:00Z から始まる時計。 */
    public static MutableClock start() {
        return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    }

    public synchronized void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }
}
