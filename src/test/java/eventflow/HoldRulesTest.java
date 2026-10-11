package eventflow;

import java.time.Duration;

import eventflow.admin.Event;
import eventflow.contract.Money;
import eventflow.core.event.TicketHeld;
import eventflow.core.inventory.HoldResult;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.Reservation;
import eventflow.core.order.ReservationState;

public final class HoldRulesTest {

    @Test
    public void kenjiHoldsTwoVip_seatsLeaveThePoolAtHoldTime() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");

        HoldResult result = app.inventory().hold(kenji, vip.tierId(), 2);

        Check.that(result.held(), "hold should succeed");
        Check.equal(2, vip.heldQuantity(), "held (drops at hold, not payment)");
        Check.equal(498, vip.remaining(), "remaining");
        Check.equal(Money.baht(13_000), result.reservation().totalAmount(), "total");
        Check.equal(ReservationState.HELD, result.reservation().state(), "state");
        Check.that(app.bus().history().stream().anyMatch(e -> e instanceof TicketHeld), "TicketHeld published");
    }

    @Test
    public void expiryIsStampedByTheServer_exactlyTenMinutes() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");

        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        Check.equal(clock.instant(), r.heldAt(), "heldAt");
        Check.equal(clock.instant().plus(Duration.ofMinutes(10)), r.expiresAt(), "expiresAt");
    }

    @Test
    public void fiveTickets_refused_overOrderLimit() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");

        Check.equal(HoldResult.Outcome.OVER_ORDER_LIMIT,
                app.inventory().hold(kenji, vip.tierId(), 5).outcome(), "over the 4-ticket limit");
        Check.equal(HoldResult.Outcome.HELD,
                app.inventory().hold(kenji, vip.tierId(), 4).outcome(), "4 is allowed");
    }

    @Test
    public void moreThanRemaining_refused_andNothingIsHeld() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 3);
        String kenji = Fixtures.admit(app, vip, "kenji");
        app.inventory().hold(Fixtures.admit(app, vip, "fan-a"), vip.tierId(), 2);

        HoldResult result = app.inventory().hold(kenji, vip.tierId(), 2);

        Check.equal(HoldResult.Outcome.NOT_ENOUGH_SEATS, result.outcome(), "not enough seats");
        Check.equal(1, vip.remaining(), "no partial hold");
    }

    @Test
    public void oneTokenBuysOnce() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        app.inventory().hold(kenji, vip.tierId(), 2);

        Check.equal(HoldResult.Outcome.TOKEN_ALREADY_USED,
                app.inventory().hold(kenji, vip.tierId(), 2).outcome(), "one token buys once");
        Check.equal(2, vip.heldQuantity(), "held");
    }

    @Test
    public void saleWithNoTiers_cannotOpen() {
        EventFlow app = new EventFlow(Fixtures.clock());
        Event concert = Fixtures.blackpink(app);
        try {
            app.events().openSale(concert.eventId());
            throw new AssertionError("opening a sale with no tiers should be refused");
        } catch (IllegalStateException expected) {
            Check.that(!concert.onSale(), "still not on sale");
        }
    }
}
