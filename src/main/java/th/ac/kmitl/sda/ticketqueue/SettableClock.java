package th.ac.kmitl.sda.ticketqueue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

// A clock we can move forward, so ten minutes can pass instantly.
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
