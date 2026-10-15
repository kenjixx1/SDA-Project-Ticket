package eventflow;

import java.time.Duration;
import java.util.List;

import eventflow.contract.PaymentProvider;
import eventflow.contract.PaymentRequest;
import eventflow.contract.PaymentResult;
import eventflow.core.event.DomainEvent;
import eventflow.core.event.OrderConfirmed;
import eventflow.core.event.PaymentCompleted;
import eventflow.core.event.PaymentVoided;
import eventflow.core.event.TicketHeld;
import eventflow.core.event.TicketIssued;
import eventflow.core.event.UserAdmitted;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.CheckoutResult;
import eventflow.core.order.Reservation;
import eventflow.core.order.ReservationState;
import eventflow.core.registry.UnknownPaymentProviderException;
import eventflow.plugin.payment.CardPlugin;
import eventflow.plugin.payment.PromptPayPlugin;

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
        Check.equal(List.of(UserAdmitted.class, TicketHeld.class, PaymentCompleted.class, OrderConfirmed.class, TicketIssued.class),
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

    @Test
    // The bank answers 6 seconds after the hold expired.
    public void bankAnswersAfterExpiry_paymentVoided_seatsBackOnSale() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        SlowBank bank = new SlowBank(clock, Duration.ofSeconds(8));
        app.registry().register(bank);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();
        clock.advance(Duration.ofMinutes(9).plusSeconds(58));  // 2 seconds left

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
    public void payByCard_secondPlugInWorksTheSameWay() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        Check.equal(CheckoutResult.CONFIRMED, app.checkout().checkout(r.reservationId(), "card"), "checkout");
        CardPlugin card = (CardPlugin) app.registry().payment("card");
        Check.equal(CardPlugin.Status.CAPTURED, card.status("CARD-" + r.reservationId()), "card payment");
        Check.equal(2, vip.soldQuantity(), "sold");
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

    private static final class DecliningBank implements PaymentProvider {
        @Override public String key() { return "declining"; }
        @Override public PaymentResult authorise(PaymentRequest request) {
            return PaymentResult.declined("insufficient funds");
        }
        @Override public void capture(String ref) { throw new AssertionError("never captured"); }
        @Override public void voidAuthorisation(String ref) { }
    }
}
