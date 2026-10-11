package th.ac.kmitl.sda.ticketqueue;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import th.ac.kmitl.sda.ticketqueue.admin.Event;
import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.event.DomainEvent;
import th.ac.kmitl.sda.ticketqueue.core.event.OrderConfirmed;
import th.ac.kmitl.sda.ticketqueue.core.event.PaymentCompleted;
import th.ac.kmitl.sda.ticketqueue.core.event.TicketHeld;
import th.ac.kmitl.sda.ticketqueue.core.event.UserAdmitted;
import th.ac.kmitl.sda.ticketqueue.core.inventory.HoldResult;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.CheckoutResult;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.queue.JoinResult;
import th.ac.kmitl.sda.ticketqueue.core.queue.QueueStatus;
import th.ac.kmitl.sda.ticketqueue.core.queue.QueueTokenState;

/** Joining the waiting room, and how the queue hands over to the hold. */
public final class QueueTest {

    @Test
    public void kenjiJoinsBehind4181Fans_position4182() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        for (int i = 1; i <= 4_181; i++) {
            app.queue().join("fan-" + i, vip.eventId());
        }

        JoinResult kenji = app.queue().join("kenji", vip.eventId());
        QueueStatus status = app.queue().status(kenji.token().tokenId());

        Check.equal(JoinResult.Outcome.JOINED, kenji.outcome(), "join");
        Check.equal(4_182L, status.position(), "position");
        Check.equal(Duration.ofSeconds(21), status.estimatedWait(), "wait: 4,182 ÷ 200 per second");

        app.admission().admitBatch(vip.eventId());
        Check.equal(3_982L, app.queue().status(kenji.token().tokenId()).position(), "after one batch");
    }

    @Test
    public void tenTabsAtOnce_oneToken() throws Exception {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        for (int tab = 0; tab < 10; tab++) {
            pool.submit(() -> {
                go.await();
                tokens.add(app.queue().join("kenji", vip.eventId()).token().tokenId());
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        Check.that(pool.awaitTermination(10, TimeUnit.SECONDS), "tabs did not finish");

        Check.equal(1, tokens.size(), "tokens for ten tabs");
        Check.equal(1, app.queue().queueLength(vip.eventId()), "places in line");
    }

    @Test
    public void comingBack_samePlace() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        app.queue().join("fan-1", vip.eventId());
        JoinResult first = app.queue().join("kenji", vip.eventId());

        JoinResult again = app.queue().join("kenji", vip.eventId());

        Check.equal(JoinResult.Outcome.ALREADY_IN_QUEUE, again.outcome(), "outcome");
        Check.equal(first.token().tokenId(), again.token().tokenId(), "same token");
        Check.equal(2L, app.queue().status(again.token().tokenId()).position(), "same place");
    }

    @Test
    public void saleNotOpen_noToken() {
        EventFlow app = new EventFlow(Fixtures.clock());
        Event concert = Fixtures.blackpink(app);
        app.events().addTier(concert.eventId(), "VIP", Money.baht(6_500), 500);

        Check.equal(JoinResult.Outcome.SALE_NOT_OPEN,
                app.queue().join("kenji", concert.eventId()).outcome(), "sale not open");
    }

    @Test
    public void soldOut_noToken() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 2);
        app.inventory().hold(Fixtures.admit(app, vip, "fan-1"), vip.tierId(), 2);

        Check.equal(JoinResult.Outcome.SOLD_OUT,
                app.queue().join("kenji", vip.eventId()).outcome(), "sold out");
    }

    /** Fairness: 10,000 tokens, batches of 200, admitted order = issued order. */
    @Test
    public void tenThousandFans_admittedInExactlyTheOrderTheyJoined() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        List<String> admittedOrder = new CopyOnWriteArrayList<>();
        app.bus().subscribe(UserAdmitted.class, e -> admittedOrder.add(e.tokenId()));
        List<String> joinedOrder = new java.util.ArrayList<>();
        for (int i = 1; i <= 10_000; i++) {
            joinedOrder.add(app.queue().join("fan-" + i, vip.eventId()).token().tokenId());
        }

        int batches = 0;
        int admitted;
        while ((admitted = app.admission().admitBatch(vip.eventId())) > 0) {
            Check.that(admitted <= 200, "batch larger than the admission rate: " + admitted);
            batches++;
        }

        Check.equal(50, batches, "batches of 200");
        Check.equal(joinedOrder, admittedOrder, "admitted order vs joined order");
    }

    @Test
    public void chaiLowersTheRate_smallerBatches() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        for (int i = 1; i <= 300; i++) {
            app.queue().join("fan-" + i, vip.eventId());
        }

        app.events().setAdmissionRate(vip.eventId(), 50);

        Check.equal(50, app.admission().admitBatch(vip.eventId()), "batch size");
        Check.equal(250, app.queue().queueLength(vip.eventId()), "still waiting");
    }

    @Test
    public void stillWaiting_cannotHold() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String waiting = app.queue().join("kenji", vip.eventId()).token().tokenId();

        HoldResult result = app.inventory().hold(waiting, vip.tierId(), 2);

        Check.equal(HoldResult.Outcome.NOT_ADMITTED, result.outcome(), "a waiting token cannot hold");
        Check.equal(500, vip.remaining(), "remaining");
    }

    @Test
    public void admittedButIdleFiveMinutes_tokenExpires_mustRejoin() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        Check.equal(0, app.admission().expireIdle(), "not yet");
        clock.advance(Duration.ofSeconds(1));
        Check.equal(1, app.admission().expireIdle(), "expired after 5 minutes");

        Check.equal(HoldResult.Outcome.NOT_ADMITTED,
                app.inventory().hold(kenji, vip.tierId(), 2).outcome(), "old token");
        JoinResult rejoin = app.queue().join("kenji", vip.eventId());
        Check.equal(JoinResult.Outcome.JOINED, rejoin.outcome(), "rejoin at the back");
        Check.that(!rejoin.token().tokenId().equals(kenji), "a new token");
    }

    @Test
    public void holdExpires_tokenExpiresToo() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        app.inventory().hold(kenji, vip.tierId(), 2);

        clock.advance(Duration.ofMinutes(10));
        app.expiry().sweep();

        Check.equal(QueueTokenState.EXPIRED, app.queue().token(kenji).state(), "token state");
        Check.equal(JoinResult.Outcome.JOINED,
                app.queue().join("kenji", vip.eventId()).outcome(), "may rejoin");
    }

    /** The brief's primary workflow, start to finish. */
    @Test
    public void joinToPaid_endToEnd() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);

        String kenji = app.queue().join("kenji", vip.eventId()).token().tokenId();
        Check.equal(1, app.admission().admitBatch(vip.eventId()), "admitted");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();
        CheckoutResult paid = app.checkout().checkout(r.reservationId(), "promptpay");

        Check.equal(CheckoutResult.CONFIRMED, paid, "checkout");
        Check.equal(QueueTokenState.SPENT, app.queue().token(kenji).state(), "token spent");
        Check.equal(kenji, r.tokenId(), "the token carries the purchase");
        List<Class<?>> order = app.bus().history().stream()
                .<Class<?>>map(DomainEvent::getClass).toList();
        Check.equal(List.of(UserAdmitted.class, TicketHeld.class,
                PaymentCompleted.class, OrderConfirmed.class), order, "event order");
    }
}
