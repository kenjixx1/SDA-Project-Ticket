package eventflow;

import java.time.Duration;

import eventflow.core.event.HoldExpired;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.CheckoutResult;
import eventflow.core.order.Reservation;
import eventflow.core.order.ReservationState;

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

        // No sweep has run yet. Checkout notices the hold is dead by itself.
        CheckoutResult result = app.checkout().checkout(r.reservationId(), "promptpay");

        Check.equal(CheckoutResult.HOLD_EXPIRED, result, "checkout");
        Check.equal(0, vip.soldQuantity(), "sold");
        Check.equal(500, vip.remaining(), "remaining");
        Check.invariants(app, vip);
    }
}
