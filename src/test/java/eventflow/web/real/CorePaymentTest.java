package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorePaymentTest {

    private FakeClock clock;
    private CoreBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new CoreBookingGateway(new RealEngine(clock));
    }

    private HoldView twoVip(String customer) {
        clock.advance(60_000);
        String token = gw.joinQueue(customer, "the-1975").token();
        clock.advance(2_000);
        String vip = gw.availability("the-1975").stream().filter(t -> t.name().equals("VIP"))
                .findFirst().orElseThrow().tierId();
        return gw.hold(customer, token, vip, 2);
    }

    @Test
    void promptPayConfirmsTheOrderInCore() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals(13_000, start.amount());
        assertEquals("PROMPTPAY|" + start.paymentId() + "|13000", start.display());
        assertEquals("PENDING", gw.paymentStatus("kenji", start.paymentId()).state());

        var paid = gw.answerPayment("kenji", start.paymentId(), true);
        assertEquals("CAPTURED", paid.state());
        assertTrue(paid.answered());
        assertEquals("CONFIRMED", gw.getHold("kenji", h.reservationId()).state());

        var orders = gw.orders("kenji");
        assertEquals(1, orders.size());
        assertEquals(paid.orderId(), orders.get(0).orderId());
        assertEquals(13_000, orders.get(0).paidAmount());
        assertEquals("PromptPay", orders.get(0).providerLabel());
        assertEquals(2, orders.get(0).tickets().size());
        assertEquals("VALID", orders.get(0).tickets().get(0).state());
    }

    @Test
    void cardIsNotAvailableOnCore() {
        HoldView h = twoVip("kenji");
        GatewayException e = assertThrows(GatewayException.class,
                () -> gw.startPayment("kenji", h.reservationId(), "card"));
        assertEquals("PROVIDER_UNAVAILABLE", e.code);
    }

    @Test
    void decliningDoesNotReachCore() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals("DECLINED", gw.answerPayment("kenji", start.paymentId(), false).state());
        assertEquals("HELD", gw.getHold("kenji", h.reservationId()).state());
    }

    @Test
    void payingAfterTheTenMinutesIsVoided() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        clock.advance(10 * 60_000L);
        var status = gw.answerPayment("kenji", start.paymentId(), true);
        assertEquals("VOIDED", status.state());
        assertNull(status.orderId());
        assertEquals(0, gw.orders("kenji").size());
    }

    @Test
    void startingAgainReturnsTheSamePendingPayment() {
        HoldView h = twoVip("kenji");
        var first = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals(first.paymentId(), gw.startPayment("kenji", h.reservationId(), "promptpay").paymentId());
        assertEquals(first.paymentId(), gw.getHold("kenji", h.reservationId()).paymentId());
    }

    @Test
    void releasingSeatsEarlyIsNotInCore() {
        HoldView h = twoVip("kenji");
        GatewayException e = assertThrows(GatewayException.class, () -> gw.release("kenji", h.reservationId()));
        assertEquals("NOT_IN_CORE", e.code);
        assertEquals(409, e.status);
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.release("mint", h.reservationId())).status);
    }

    @Test
    void someoneElsesPaymentIsNotFound() {
        HoldView h = twoVip("kenji");
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals(404, assertThrows(GatewayException.class,
                () -> gw.paymentStatus("mint", start.paymentId())).status);
    }
}
