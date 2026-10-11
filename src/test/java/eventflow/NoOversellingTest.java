package eventflow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import eventflow.core.inventory.HoldResult;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.CheckoutResult;
import eventflow.core.order.Reservation;

// 200 buyers race for the last seats. The answer must be exact on every run.
public final class NoOversellingTest {

    private static final int RUNS = 50;

    @Test
    public void twoHundredBuyersOneSeatEach_exactlyTenSold_everyRun() throws Exception {
        for (int run = 1; run <= RUNS; run++) {
            race(10, 1, 200, 10, run);
        }
    }

    @Test
    public void twoHundredBuyersTwoSeatsEach_exactlyFiveHolds_everyRun() throws Exception {
        for (int run = 1; run <= RUNS; run++) {
            race(10, 2, 200, 5, run);
        }
    }

    private static void race(int seats, int perBuyer, int buyers, int expectedHolds, int run)
            throws Exception {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, seats);
        List<String> tokens = Fixtures.admitFans(app, vip, buyers);
        List<Reservation> won = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch ready = new CountDownLatch(buyers);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < buyers; i++) {
            String token = tokens.get(i);
            pool.submit(() -> {
                ready.countDown();
                go.await();  // everyone presses at the same moment
                HoldResult result = app.inventory().hold(token, vip.tierId(), perBuyer);
                if (result.held()) {
                    won.add(result.reservation());
                }
                return null;
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        Check.that(pool.awaitTermination(30, TimeUnit.SECONDS), "buyers did not finish");

        String where = " (run " + run + ")";
        Check.equal(expectedHolds, won.size(), "successful holds" + where);
        Check.equal(0, vip.remaining(), "remaining" + where);
        Check.invariants(app, vip);

        for (Reservation r : won) {
            Check.equal(CheckoutResult.CONFIRMED,
                    app.checkout().checkout(r.reservationId(), "promptpay"), "checkout" + where);
        }
        Check.equal(seats, vip.soldQuantity(), "sold" + where);
        Check.equal(0, vip.heldQuantity(), "held after paying" + where);
        Check.invariants(app, vip);
    }
}
