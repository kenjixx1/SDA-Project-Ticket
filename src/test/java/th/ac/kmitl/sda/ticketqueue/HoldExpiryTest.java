package th.ac.kmitl.sda.ticketqueue;

import java.time.Duration;

import th.ac.kmitl.sda.ticketqueue.core.event.HoldExpired;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.CheckoutResult;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationState;

/** An abandoned hold gives its seats back after exactly ten minutes. */
public final class HoldExpiryTest {

    @Test
    public void beforeTenMinutes_nothingExpires() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));

        Check.equal(0, app.expiry().sweep(), "expired holds");
        Check.equal(ReservationState.HELD, r.state(), "state");
        Check.equal(2, vip.heldQuantity(), "held");
    }

    @Test
    public void atTenMinutes_seatsReturnToSale() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        clock.advance(Duration.ofMinutes(10));

        Check.equal(1, app.expiry().sweep(), "expired holds");
        Check.equal(ReservationState.EXPIRED, r.state(), "state");
        Check.equal(500, vip.remaining(), "remaining (back on sale)");
        Check.that(app.bus().history().stream().anyMatch(e -> e instanceof HoldExpired),
                "HoldExpired published");
        Check.equal(0, app.expiry().sweep(), "a second sweep finds nothing");
        Check.invariants(app, vip);
    }

    @Test
    public void payingAfterExpiry_isRefused_andNotCharged() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();
        clock.advance(Duration.ofMinutes(11));

        // No sweep has run yet: checkout itself notices the hold is dead.
        CheckoutResult result = app.checkout().checkout(r.reservationId(), "promptpay");

        Check.equal(CheckoutResult.HOLD_EXPIRED, result, "checkout");
        Check.equal(0, vip.soldQuantity(), "sold");
        Check.equal(500, vip.remaining(), "remaining");
        Check.invariants(app, vip);
    }
}
