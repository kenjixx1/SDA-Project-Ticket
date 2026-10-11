package eventflow.web.fake;

import java.util.function.LongSupplier;

/** A clock for the demo. It can run faster than real time and can jump forward. */
public final class FakeClock {

    private final LongSupplier realMillis;
    private long baseFake;
    private long baseReal;
    private int speed = 1;

    public FakeClock(long startFakeMillis, LongSupplier realMillis) {
        this.realMillis = realMillis;
        this.baseFake = startFakeMillis;
        this.baseReal = realMillis.getAsLong();
    }

    public synchronized long now() {
        return baseFake + (realMillis.getAsLong() - baseReal) * speed;
    }

    public synchronized int speed() {
        return speed;
    }

    public synchronized void setSpeed(int times) {
        rebase();
        speed = times;
    }

    public synchronized void set(long fakeMillis) {
        baseFake = fakeMillis;
        baseReal = realMillis.getAsLong();
    }

    public synchronized void advance(long millis) {
        rebase();
        baseFake += millis;
    }

    private void rebase() {
        baseFake = now();
        baseReal = realMillis.getAsLong();
    }
}
