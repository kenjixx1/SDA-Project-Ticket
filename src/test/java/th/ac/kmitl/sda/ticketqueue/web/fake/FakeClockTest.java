package th.ac.kmitl.sda.ticketqueue.web.fake;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeClockTest {

    @Test
    void runsAtTheChosenSpeedAndCanJump() {
        AtomicLong real = new AtomicLong(1_000);
        FakeClock clock = new FakeClock(50_000, real::get);

        real.addAndGet(2_000);
        assertEquals(52_000, clock.now());

        clock.setSpeed(100);
        real.addAndGet(1_000);
        assertEquals(152_000, clock.now());

        clock.advance(8_000);
        assertEquals(160_000, clock.now());

        clock.set(10);
        assertEquals(10, clock.now());
        assertEquals(100, clock.speed());
    }
}
