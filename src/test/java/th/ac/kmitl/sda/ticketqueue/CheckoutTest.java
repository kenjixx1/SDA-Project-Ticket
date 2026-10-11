package th.ac.kmitl.sda.ticketqueue;

import java.time.Duration;
import java.util.List;

import th.ac.kmitl.sda.ticketqueue.contract.PaymentProvider;
import th.ac.kmitl.sda.ticketqueue.contract.PaymentRequest;
import th.ac.kmitl.sda.ticketqueue.contract.PaymentResult;
import th.ac.kmitl.sda.ticketqueue.core.event.DomainEvent;
import th.ac.kmitl.sda.ticketqueue.core.event.OrderConfirmed;
import th.ac.kmitl.sda.ticketqueue.core.event.PaymentCompleted;
import th.ac.kmitl.sda.ticketqueue.core.event.PaymentVoided;
import th.ac.kmitl.sda.ticketqueue.core.event.TicketHeld;
import th.ac.kmitl.sda.ticketqueue.core.event.UserAdmitted;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.CheckoutResult;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationState;
import th.ac.kmitl.sda.ticketqueue.core.registry.UnknownPaymentProviderException;
import th.ac.kmitl.sda.ticketqueue.plugin.payment.PromptPayPlugin;

/** Checkout and pay. */
public final class CheckoutTest {

    @Test
    public void kenjiPays_seatsMoveFromHeldToSold_eventsInOrder() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        CheckoutResult result = app.checkout().checkout(r.reservationId(), "promptpay");

        Check.equal(CheckoutResult.CONFIRMED, result, "checkout");
        Check.equal(0, vip.heldQuantity(), "held");
        Check.equal(2, vip.soldQuantity(), "sold");
        List<Class<?>> order = app.bus().history().stream()
                .<Class<?>>map(DomainEvent::getClass).toList();
        Check.equal(List.of(UserAdmitted.class, TicketHeld.class, PaymentCompleted.class, OrderConfirmed.class),
                order, "event order");
        Check.invariants(app, vip);
    }

    @Test
    public void doubleTapOnPay_chargedOnce() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        app.checkout().checkout(r.reservationId(), "promptpay");
        CheckoutResult second = app.checkout().checkout(r.reservationId(), "promptpay");

        Check.equal(CheckoutResult.CONFIRMED, second, "second tap");
        long confirmations = app.bus().history().stream()
                .filter(e -> e instanceof OrderConfirmed).count();
        Check.equal(1L, confirmations, "OrderConfirmed count");
        Check.equal(2, vip.soldQuantity(), "sold");
    }

    /** The bank is slow: its answer arrives after the hold has expired. */
    @Test
    public void bankAnswersAfterExpiry_paymentVoided_seatsBackOnSale() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        SlowBank bank = new SlowBank(clock, Duration.ofSeconds(8));
        app.registry().register(bank);  // a new plug-in, zero core changes
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();
        clock.advance(Duration.ofMinutes(9).plusSeconds(58));  // 10:14:38, 2 s left

        CheckoutResult result = app.checkout().checkout(r.reservationId(), "slowbank");

        Check.equal(CheckoutResult.HOLD_EXPIRED, result, "checkout");
        Check.equal(ReservationState.EXPIRED, r.state(), "state");
        Check.that(bank.voided, "the late authorisation was voided");
        Check.that(!bank.captured, "Kenji was not charged");
        Check.that(app.bus().history().stream().anyMatch(e -> e instanceof PaymentVoided),
                "PaymentVoided published");
        Check.equal(500, vip.remaining(), "remaining");
        Check.invariants(app, vip);
    }

    @Test
    public void declinedPayment_holdKeepsItsTime() {
        EventFlow app = new EventFlow(Fixtures.clock());
        app.registry().register(new DecliningBank());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        Check.equal(CheckoutResult.DECLINED,
                app.checkout().checkout(r.reservationId(), "declining"), "first try");
        Check.equal(ReservationState.HELD, r.state(), "still held");
        Check.equal(CheckoutResult.CONFIRMED,
                app.checkout().checkout(r.reservationId(), "promptpay"), "second try");
    }

    @Test
    public void capturedOnlyAfterConfirm() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();
        app.checkout().checkout(r.reservationId(), "promptpay");

        PromptPayPlugin promptPay = (PromptPayPlugin) app.registry().payment("promptpay");
        Check.equal(PromptPayPlugin.Status.CAPTURED, promptPay.status("PP-" + r.reservationId()),
                "payment status");
    }

    @Test
    public void unknownProviderKey_isRefused() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();
        try {
            app.checkout().checkout(r.reservationId(), "paypal");
            throw new AssertionError("expected UnknownPaymentProviderException");
        } catch (UnknownPaymentProviderException expected) {
            Check.equal(ReservationState.HELD, r.state(), "hold untouched");
        }
    }

    /** A test plug-in whose bank takes a while to answer. */
    private static final class SlowBank implements PaymentProvider {
        private final SettableClock clock;
        private final Duration delay;
        volatile boolean captured;
        volatile boolean voided;

        SlowBank(SettableClock clock, Duration delay) {
            this.clock = clock;
            this.delay = delay;
        }

        @Override public String key() { return "slowbank"; }

        @Override public PaymentResult authorise(PaymentRequest request) {
            clock.advance(delay);
            return PaymentResult.authorised("SB-" + request.reservationId());
        }

        @Override public void capture(String ref)           { captured = true; }
        @Override public void voidAuthorisation(String ref) { voided = true; }
    }

    /** A test plug-in that always says no. */
    private static final class DecliningBank implements PaymentProvider {
        @Override public String key() { return "declining"; }
        @Override public PaymentResult authorise(PaymentRequest request) {
            return PaymentResult.declined("insufficient funds");
        }
        @Override public void capture(String ref) { throw new AssertionError("never captured"); }
        @Override public void voidAuthorisation(String ref) { }
    }
}
