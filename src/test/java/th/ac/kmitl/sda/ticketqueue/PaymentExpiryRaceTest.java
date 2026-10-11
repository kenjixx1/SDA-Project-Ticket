package th.ac.kmitl.sda.ticketqueue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationState;
import th.ac.kmitl.sda.ticketqueue.plugin.payment.PromptPayPlugin;

/**
 * Payments and the expiry sweep race for the same holds.
 *
 * <p>Who wins each race differs from run to run. What must never differ:
 * every hold ends either paid-and-sold or expired-and-returned, and money and
 * seats always move together.
 */
public final class PaymentExpiryRaceTest {

    private static final int RUNS = 20;
    private static final int HOLDS = 200;

    @Test
    public void payAndExpireAtTheSameMoment_moneyAndSeatsAlwaysAgree() throws Exception {
        int confirmed = 0;
        for (int run = 1; run <= RUNS; run++) {
            confirmed += raceOnce(run);
        }
        int total = RUNS * HOLDS;
        // Guard against a test that never really races: both sides must win sometimes.
        Check.that(confirmed > 0 && confirmed < total,
                "expected a mix of paid and expired holds, got " + confirmed + " paid of " + total);
    }

    /** Returns how many holds ended up paid. */
    private static int raceOnce(int run) throws Exception {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        List<Reservation> holds = new ArrayList<>();
        for (String token : Fixtures.admitFans(app, vip, HOLDS)) {
            holds.add(app.inventory().hold(token, vip.tierId(), 2).reservation());
        }
        clock.advance(Duration.ofMinutes(9).plusSeconds(59));  // one second left

        ExecutorService pool = Executors.newFixedThreadPool(HOLDS + 1);
        CountDownLatch go = new CountDownLatch(1);
        AtomicBoolean paying = new AtomicBoolean(true);
        AtomicInteger started = new AtomicInteger();
        for (Reservation r : holds) {
            pool.submit(() -> {
                go.await();
                started.incrementAndGet();
                app.checkout().checkout(r.reservationId(), "promptpay");
                return null;
            });
        }
        pool.submit(() -> {  // the deadline passes while half the buyers are mid-payment
            go.await();
            while (started.get() < HOLDS / 2) {
                Thread.onSpinWait();
            }
            clock.advance(Duration.ofSeconds(1));
            while (paying.get()) {
                app.expiry().sweep();
            }
            return null;
        });
        go.countDown();
        pool.shutdown();
        Thread.sleep(50);
        paying.set(false);
        Check.that(pool.awaitTermination(30, TimeUnit.SECONDS), "race did not finish");
        app.expiry().sweep();

        PromptPayPlugin promptPay = (PromptPayPlugin) app.registry().payment("promptpay");
        String where = " (run " + run + ")";
        for (Reservation r : holds) {
            PromptPayPlugin.Status money = promptPay.status("PP-" + r.reservationId());
            if (r.state() == ReservationState.CONFIRMED) {
                Check.equal(PromptPayPlugin.Status.CAPTURED, money, r + " paid" + where);
            } else {
                Check.equal(ReservationState.EXPIRED, r.state(), r + " state" + where);
                Check.that(money != PromptPayPlugin.Status.CAPTURED,
                        r + " expired but was charged" + where);
            }
        }
        Check.invariants(app, vip);
        Check.equal(0, vip.heldQuantity(), "nothing left held" + where);
        return vip.soldQuantity() / 2;
    }
}
