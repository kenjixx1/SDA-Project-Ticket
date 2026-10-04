package th.ac.kmitl.sda.ticketqueue;

import java.time.Duration;

import th.ac.kmitl.sda.ticketqueue.admin.Event;
import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.event.TicketHeld;
import th.ac.kmitl.sda.ticketqueue.core.inventory.HoldResult;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationState;

/** UC-02 · the rules on a single hold. */
public final class HoldRulesTest {

    @Test
    public void kenjiHoldsTwoVip_seatsLeaveThePoolAtHoldTime() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);

        HoldResult result = app.inventory().hold("token-kenji", vip.tierId(), 2);

        Check.that(result.held(), "hold should succeed");
        Check.equal(2, vip.heldQuantity(), "held (FR-09: drops at hold, not payment)");
        Check.equal(498, vip.remaining(), "remaining");
        Check.equal(Money.baht(13_000), result.reservation().totalAmount(), "total");
        Check.equal(ReservationState.HELD, result.reservation().state(), "state");
        Check.that(app.bus().history().get(0) instanceof TicketHeld, "TicketHeld published");
    }

    @Test
    public void expiryIsStampedByTheServer_exactlyTenMinutes() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);

        Reservation r = app.inventory().hold("token-kenji", vip.tierId(), 2).reservation();

        Check.equal(clock.instant(), r.heldAt(), "heldAt");
        Check.equal(clock.instant().plus(Duration.ofMinutes(10)), r.expiresAt(), "expiresAt (R-E04-3)");
    }

    @Test
    public void fiveTickets_refused_overOrderLimit() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);

        Check.equal(HoldResult.Outcome.OVER_ORDER_LIMIT,
                app.inventory().hold("token-kenji", vip.tierId(), 5).outcome(), "FR-11");
        Check.equal(HoldResult.Outcome.HELD,
                app.inventory().hold("token-kenji", vip.tierId(), 4).outcome(), "4 is allowed");
    }

    @Test
    public void moreThanRemaining_refused_andNothingIsHeld() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 3);
        app.inventory().hold("token-a", vip.tierId(), 2);

        HoldResult result = app.inventory().hold("token-kenji", vip.tierId(), 2);

        Check.equal(HoldResult.Outcome.NOT_ENOUGH_SEATS, result.outcome(), "FR-10");
        Check.equal(1, vip.remaining(), "no partial hold (R-E04-2)");
    }

    @Test
    public void oneTokenBuysOnce() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        app.inventory().hold("token-kenji", vip.tierId(), 2);

        Check.equal(HoldResult.Outcome.TOKEN_ALREADY_USED,
                app.inventory().hold("token-kenji", vip.tierId(), 2).outcome(), "locked decision 3");
        Check.equal(2, vip.heldQuantity(), "held");
    }

    @Test
    public void saleWithNoTiers_cannotOpen() {
        EventFlow app = new EventFlow(Fixtures.clock());
        Event concert = Fixtures.blackpink(app);
        try {
            app.events().openSale(concert.eventId());
            throw new AssertionError("FR-25: opening a sale with no tiers should be refused");
        } catch (IllegalStateException expected) {
            Check.that(!concert.onSale(), "still not on sale");
        }
    }
}
