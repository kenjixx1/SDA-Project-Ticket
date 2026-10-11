package eventflow.web.real;

import eventflow.web.fake.FakeClock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Lets core read the demo clock, so "+10 min" on the demo panel also ends core's 10-minute holds. */
final class EngineClock extends Clock {

    private final FakeClock demo;

    EngineClock(FakeClock demo) {
        this.demo = demo;
    }

    @Override public Instant instant()           { return Instant.ofEpochMilli(demo.now()); }
    @Override public ZoneId getZone()            { return RealEngine.BANGKOK; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
