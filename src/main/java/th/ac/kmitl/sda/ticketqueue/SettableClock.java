package th.ac.kmitl.sda.ticketqueue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A server clock that can be moved forward by hand, so the demo and the tests
 * can show ten minutes passing without waiting ten minutes.
 */
public final class SettableClock extends Clock {

    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private volatile Instant now;

    public SettableClock(Instant start) {
        this.now = start;
    }

    public synchronized void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override public Instant instant()          { return now; }
    @Override public ZoneId getZone()           { return BANGKOK; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
