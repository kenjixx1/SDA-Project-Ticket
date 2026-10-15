package eventflow;

import eventflow.core.inventory.TicketTier;
import eventflow.core.order.CheckoutResult;
import eventflow.core.order.Reservation;
import eventflow.core.order.ReservationState;
import eventflow.core.queue.JoinResult;
import eventflow.core.queue.QueueTokenState;

public final class ReleaseTest {

    @Test
    public void userGivesSeatsBack_backOnSaleRightAway() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        Check.that(app.reservations().release(r.reservationId()), "released");

        Check.equal(ReservationState.RELEASED, r.state(), "state");
        Check.equal(500, vip.remaining(), "seats back on sale");
        Check.equal(QueueTokenState.EXPIRED, app.queue().token(kenji).state(), "token ended");
        Check.equal(JoinResult.Outcome.JOINED, app.queue().join("kenji", vip.eventId()).outcome(), "may rejoin");
        Check.invariants(app, vip);
    }

    @Test
    public void afterRelease_cannotPay() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();
        app.reservations().release(r.reservationId());

        Check.equal(CheckoutResult.HOLD_EXPIRED, app.checkout().checkout(r.reservationId(), "promptpay"), "pay");
        Check.equal(0, vip.soldQuantity(), "sold");
    }

    @Test
    public void afterPaying_cannotRelease() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();
        app.checkout().checkout(r.reservationId(), "promptpay");

        Check.that(!app.reservations().release(r.reservationId()), "release refused");
        Check.equal(2, vip.soldQuantity(), "still sold");
    }
}
