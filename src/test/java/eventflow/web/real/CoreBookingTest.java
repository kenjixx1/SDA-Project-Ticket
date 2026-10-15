package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreBookingTest {

    private FakeClock clock;
    private CoreBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new CoreBookingGateway(new RealEngine(clock));
    }

    private String admitted(String customer) {
        clock.advance(60_000);
        String token = gw.joinQueue(customer, "the-1975").token();
        clock.advance(2_000);
        return token;
    }

    private String vip() {
        return gw.availability("the-1975").stream().filter(t -> t.name().equals("VIP")).findFirst().orElseThrow().tierId();
    }

    @Test
    void m01ListsTheFourEventsCheapestFirstPrice() {
        var list = gw.listEvents("kenji");
        assertEquals(4, list.size());
        assertEquals("the-1975", list.get(0).eventId());
        assertEquals("SCHEDULED", list.get(0).state());
        assertEquals(1900, list.get(0).fromPrice());
        assertFalse(list.get(0).soldOut());
    }

    @Test
    void m02UnknownEventIsNotFound() {
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.getEvent("kenji", "nope")).status);
        assertEquals(3, gw.getEvent("kenji", "the-1975").tiers().size());
    }

    @Test
    void m03CannotJoinBeforeTen() {
        GatewayException e = assertThrows(GatewayException.class, () -> gw.joinQueue("kenji", "the-1975"));
        assertEquals("NOT_ON_SALE", e.code);
        assertEquals(400, e.status);
    }

    @Test
    void m03JoiningTwiceGivesTheSamePlace() {
        clock.advance(60_000);
        var first = gw.joinQueue("kenji", "the-1975");
        assertFalse(first.isExisting());
        assertEquals(1, first.position());
        var again = gw.joinQueue("kenji", "the-1975");
        assertTrue(again.isExisting());
        assertEquals(first.token(), again.token());
        assertEquals(first.token(), gw.getEvent("kenji", "the-1975").myToken());
        assertNull(gw.getEvent("mint", "the-1975").myToken());
    }

    @Test
    void m04TheQueueLetsKenjiInWithinASecond() {
        String token = admitted("kenji");
        var q = gw.queueStatus("kenji", token);
        assertEquals("ADMITTED", q.state());
        assertEquals(1, q.moved());
        assertEquals(200, q.admitPerSecond());
        assertNull(q.reservationId());
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.queueStatus("mint", token)).status);
    }

    @Test
    void m06HoldsTwoVipForTenMinutes() {
        String token = admitted("kenji");
        HoldView h = gw.hold("kenji", token, vip(), 2);
        assertEquals("HELD", h.state());
        assertEquals(11_700, h.totalAmount());  // ฿13,000 less Early Bird
        assertEquals(500, h.remainingBefore());
        assertEquals(498, h.remainingAfter());
        assertEquals(gw.now() + 10 * 60_000L, h.expiresAt());
        assertEquals(1, h.promotions().size());
        assertEquals("Early Bird −10%", h.promotions().get(0).name());
        assertEquals(1_300, h.promotions().get(0).amount());
        assertTrue(h.paymentMethods().stream().anyMatch(m -> m.key().equals("promptpay") && m.available()));
        assertTrue(h.paymentMethods().stream().anyMatch(m -> m.key().equals("card") && m.available()));
        assertEquals("SPENT", gw.queueStatus("kenji", token).state());
        assertEquals(h.reservationId(), gw.queueStatus("kenji", token).reservationId());
    }

    @Test
    void m06OneTokenHoldsOnce() {
        String token = admitted("kenji");
        gw.hold("kenji", token, vip(), 2);
        GatewayException e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, vip(), 1));
        assertEquals("NOT_ADMITTED", e.code);
    }

    @Test
    void m06FiveTicketsIsOverTheLimit() {
        String token = admitted("kenji");
        GatewayException e = assertThrows(GatewayException.class, () -> gw.hold("kenji", token, vip(), 5));
        assertEquals("OVER_LIMIT", e.code);
        assertEquals(409, e.status);
    }

    @Test
    void m07TheHoldEndsAfterTenMinutesAndTheSeatsComeBack() {
        String token = admitted("kenji");
        HoldView h = gw.hold("kenji", token, vip(), 2);
        clock.advance(10 * 60_000L);
        assertEquals("EXPIRED", gw.getHold("kenji", h.reservationId()).state());
        assertEquals(500, gw.availability("the-1975").stream()
                .filter(t -> t.name().equals("VIP")).findFirst().orElseThrow().remaining());
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.getHold("mint", h.reservationId())).status);
    }

    @Test
    void m13MeIsKenji() {
        assertEquals("Kenji", gw.me("kenji").name());
    }

    @Test
    void leavingAHoldTheEventPageLeadsBackToIt() {
        String token = admitted("kenji");
        HoldView h = gw.hold("kenji", token, vip(), 2);

        assertEquals(token, gw.getEvent("kenji", "the-1975").myToken());  // "Back to my place"
        assertEquals(h.reservationId(), gw.queueStatus("kenji", token).reservationId());  // the queue screen forwards

        gw.release("kenji", h.reservationId());
        assertNull(gw.getEvent("kenji", "the-1975").myToken());  // hold over: "Join the queue" again
    }
}
