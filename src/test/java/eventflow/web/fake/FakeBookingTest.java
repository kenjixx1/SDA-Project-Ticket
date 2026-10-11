package eventflow.web.fake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import eventflow.web.GatewayException;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.dto.Views.OrderView;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeBookingTest {

    private FakeClock clock;
    private FakeBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new FakeBookingGateway(clock);
        clock.advance(60_000); // sale is open
    }

    /** Joins every customer, then waits once until all are admitted. Returns tokens in order. */
    private List<String> admit(String... customers) {
        List<String> tokens = new java.util.ArrayList<>();
        for (String c : customers) {
            tokens.add(gw.joinQueue(c, "the-1975").token());
        }
        clock.advance(30_000);
        return tokens;
    }

    private int vipLeft() {
        return gw.availability("the-1975").stream().filter(t -> t.tierId().equals("vip")).findFirst().orElseThrow().remaining();
    }

    @Test
    void holdTakesSeatsOutAndStampsTenMinutes() {
        String token = admit("kenji").get(0);
        long before = clock.now();
        HoldView h = gw.hold("kenji", token, "vip", 2);

        assertEquals("R-1042", h.reservationId());
        assertEquals("HELD", h.state());
        assertEquals(16, h.remainingBefore());
        assertEquals(14, h.remainingAfter());
        assertEquals(before + 600_000, h.expiresAt());
        assertEquals(6500, h.unitPrice());
        assertEquals(1, h.promotions().size());
        assertEquals("Early Bird −10%", h.promotions().get(0).name());
        assertEquals(1300, h.promotions().get(0).amount());
        assertEquals(11700, h.totalAmount());
        assertEquals("The 1975 Bangkok", h.event().name());
        assertEquals("assets/the-1975-card.png", h.event().artwork());
        assertEquals(14, vipLeft());
        assertEquals("SPENT", gw.queueStatus("kenji", token).state());
    }

    @Test
    void onePlaceInLineHoldsOnce() {
        String token = admit("kenji").get(0);
        gw.hold("kenji", token, "vip", 1);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 1));
        assertEquals("NOT_ADMITTED", e.code);
    }

    @Test
    void turnPassedScenarioStopsTheHold() {
        String token = admit("kenji").get(0);
        gw.scenario(Scenario.TURN_PASSED);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 1));
        assertEquals("NOT_ADMITTED", e.code);
    }

    @Test
    void moreThanFourIsRefused() {
        String token = admit("kenji").get(0);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 5));
        assertEquals("OVER_LIMIT", e.code);
        assertEquals(409, e.status);
    }

    @Test
    void moreThanRemainIsRefusedWithTheCountLeft() {
        List<String> t = admit("kenji", "a", "b", "c", "d");
        gw.hold("a", t.get(1), "vip", 4);
        gw.hold("b", t.get(2), "vip", 4);
        gw.hold("c", t.get(3), "vip", 4);
        gw.hold("d", t.get(4), "vip", 4);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", t.get(0), "vip", 1));
        assertEquals("NOT_ENOUGH", e.code);
        assertEquals(0, e.remaining);
    }

    @Test
    void holdExpiresAfterTenMinutesAndSeatsReturn() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        clock.advance(600_000);
        assertEquals("EXPIRED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(16, vipLeft());
    }

    @Test
    void releaseReturnsSeats() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        assertEquals("RELEASED", gw.release("kenji", h.reservationId()).state());
        assertEquals(16, vipLeft());
        assertThrows(GatewayException.class, () -> gw.release("kenji", h.reservationId()));
    }

    @Test
    void approvedPaymentConfirmsOnceEvenIfAnsweredTwice() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        assertEquals("P-5001", start.paymentId());
        assertEquals(11700, start.amount());

        gw.answerPayment("kenji", start.paymentId(), true);
        gw.answerPayment("kenji", start.paymentId(), true);
        assertEquals("PENDING", gw.paymentStatus("kenji", start.paymentId()).state());

        clock.advance(2_000);
        var status = gw.paymentStatus("kenji", start.paymentId());
        assertEquals("CAPTURED", status.state());
        assertEquals("O-9001", status.orderId());
        assertEquals("CONFIRMED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(14, vipLeft());

        List<OrderView> orders = gw.orders("kenji");
        assertEquals(2, orders.size());
        OrderView bought = orders.get(0);
        assertEquals("O-9001", bought.orderId());
        assertFalse(bought.past());
        assertEquals(2, bought.tickets().size());
        assertEquals("TKT-9F42-01", bought.tickets().get(0).ticketId());
        assertEquals("TKT-9F42-02", bought.tickets().get(1).ticketId());
        assertTrue(bought.tickets().get(0).payload().startsWith("EVENTFLOW|TKT-9F42-01|the-1975|vip|kenji|"));
        assertEquals("PromptPay", bought.providerLabel());
        assertEquals(11700, bought.paidAmount());
    }

    @Test
    void declinedPaymentKeepsTheHold() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "card");
        gw.answerPayment("kenji", start.paymentId(), false);
        clock.advance(2_000);
        assertEquals("DECLINED", gw.paymentStatus("kenji", start.paymentId()).state());
        assertEquals("HELD", gw.getHold("kenji", h.reservationId()).state());
    }

    @Test
    void approvalAfterTheHoldRanOutIsVoided() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        clock.advance(599_000);
        gw.answerPayment("kenji", start.paymentId(), true);
        clock.advance(3_000);
        assertEquals("VOIDED", gw.paymentStatus("kenji", start.paymentId()).state());
        assertEquals("EXPIRED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(1, gw.orders("kenji").size());
        assertEquals(16, vipLeft());
    }

    @Test
    void raceLostRefusesOnceAndTakesSeats() {
        String token = admit("kenji").get(0);
        gw.scenario(Scenario.RACE_LOST);
        var e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, "vip", 2));
        assertEquals("RACE_LOST", e.code);
        assertEquals(14, e.remaining);
        assertNotNull(gw.hold("kenji", token, "vip", 2));
    }

    @Test
    void providerDownDimsCardOnly() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        gw.scenario(Scenario.PROVIDER_DOWN);
        var methods = gw.getHold("kenji", h.reservationId()).paymentMethods();
        assertTrue(methods.get(0).available());
        assertFalse(methods.get(1).available());
        var e = assertThrows(GatewayException.class, () -> gw.startPayment("kenji", h.reservationId(), "card"));
        assertEquals("PROVIDER_UNAVAILABLE", e.code);
    }

    @Test
    void slowBankTakesTwelveSeconds() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        gw.scenario(Scenario.BANK_SLOW);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        gw.answerPayment("kenji", start.paymentId(), true);
        clock.advance(2_000);
        assertEquals("PENDING", gw.paymentStatus("kenji", start.paymentId()).state());
        clock.advance(10_000);
        assertEquals("CAPTURED", gw.paymentStatus("kenji", start.paymentId()).state());
    }

    @Test
    void aReplacedPaymentCannotBeAnswered() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var first = gw.startPayment("kenji", h.reservationId(), "promptpay");
        var second = gw.startPayment("kenji", h.reservationId(), "card");
        assertEquals("VOIDED", gw.paymentStatus("kenji", first.paymentId()).state());
        var e = assertThrows(GatewayException.class, () -> gw.answerPayment("kenji", first.paymentId(), true));
        assertEquals("PAYMENT_REPLACED", e.code);
        gw.answerPayment("kenji", second.paymentId(), true);
        clock.advance(2_000);
        assertEquals("CAPTURED", gw.paymentStatus("kenji", second.paymentId()).state());
    }

    @Test
    void anApprovalDueBeforeExpirySettlesEvenIfNobodyAsksUntilLater() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        clock.advance(590_000);
        gw.answerPayment("kenji", start.paymentId(), true);  // settles at 592 s, before the 600 s expiry
        clock.advance(11_000);                               // nobody asks until 601 s
        assertEquals("CAPTURED", gw.paymentStatus("kenji", start.paymentId()).state());
        assertEquals("CONFIRMED", gw.getHold("kenji", h.reservationId()).state());
    }

    @Test
    void kenjiStartsWithOnePastOrder() {
        List<OrderView> orders = gw.orders("kenji");
        assertEquals(1, orders.size());
        assertEquals("O-0001", orders.get(0).orderId());
        assertTrue(orders.get(0).past());
        assertEquals("TKT-3B17-01", orders.get(0).tickets().get(0).ticketId());
        assertEquals(0, gw.orders("mint").size());
    }

    @Test
    void noPromotionAfterHalfPastTen() {
        clock.advance(30 * 60_000L);
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 2);
        assertEquals(0, h.promotions().size());
        assertEquals(13000, h.totalAmount());
    }

    @Test
    void cancelledEventMarksTicketsCancelled() {
        HoldView h = gw.hold("kenji", admit("kenji").get(0), "vip", 1);
        var start = gw.startPayment("kenji", h.reservationId(), "promptpay");
        gw.answerPayment("kenji", start.paymentId(), true);
        clock.advance(2_000);
        gw.scenario(Scenario.EVENT_CANCELLED);
        OrderView o = gw.orders("kenji").get(0);
        assertEquals("CANCELLED", o.eventState());
        assertEquals("CANCELLED", o.tickets().get(0).state());
    }
}
