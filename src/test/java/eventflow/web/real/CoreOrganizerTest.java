package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.dto.OrgRequests.EventDetails;
import eventflow.web.dto.OrgRequests.TierInput;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreOrganizerTest {

    private static final long EVENT = ZonedDateTime.of(2026, 11, 14, 19, 0, 0, 0, FakeBookingGateway.BANGKOK)
            .toInstant().toEpochMilli();
    private static final long SALE = ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, FakeBookingGateway.BANGKOK)
            .toInstant().toEpochMilli();

    private FakeClock clock;
    private CoreBookingGateway gw;
    private CoreOrganizerGateway org;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        RealEngine engine = new RealEngine(clock);
        gw = new CoreBookingGateway(engine);
        org = new CoreOrganizerGateway(engine);
    }

    private String draft() {
        return org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, null)).eventId();
    }

    @Test
    void o01ListsChaisFourEvents() {
        assertEquals(4, org.listEvents("chai").size());
        assertEquals(0, org.listEvents("mint").size());
    }

    @Test
    void o02ADraftStartsAt200AndCustomersCannotSeeIt() {
        var e = org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, null));
        assertEquals("test-night-bangkok", e.eventId());
        assertEquals("DRAFT", e.state());
        assertEquals(200, e.admitPerSecond());
        assertEquals(4, gw.listEvents("kenji").size());
        assertEquals(404, assertThrows(GatewayException.class, () -> gw.getEvent("kenji", e.eventId())).status);
    }

    @Test
    void o02SameChecksAsTheFake() {
        GatewayException e = assertThrows(GatewayException.class, () -> org.createEvent("chai",
                new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, FakeBookingGateway.START - 1, 12)));
        assertEquals("SALE_IN_PAST", e.code);
        assertEquals("saleOpensAt", e.field);
    }

    @Test
    void o05ThenO08PublishesIntoCore() {
        String id = draft();
        org.replaceTiers("chai", id, List.of(new TierInput("VIP", 6500L, 500), new TierInput("Zone A", 3800L, 2000)));
        assertEquals(2, org.getEvent("chai", id).tiers().size());
        var published = org.publish("chai", id);
        assertEquals("SCHEDULED", published.state());
        assertEquals(2500, published.tiers().stream().mapToInt(t -> t.remaining()).sum());
        assertEquals(5, gw.listEvents("kenji").size());
        assertEquals("NOT_DRAFT", assertThrows(GatewayException.class,
                () -> org.replaceTiers("chai", id, List.of(new TierInput("VIP", 1L, 1)))).code);
    }

    @Test
    void o08NeedsATier() {
        String id = draft();
        assertEquals("NO_TIERS", assertThrows(GatewayException.class, () -> org.publish("chai", id)).code);
    }

    @Test
    void o06AddsATierInCoreAfterPublishing() {
        var e = org.addTier("chai", "the-1975", new TierInput("Standing", 1500L, 1000));
        assertEquals(4, e.tiers().size());
        assertEquals(4, gw.availability("the-1975").size());
    }

    @Test
    void o04OnlyThePictureChangesAfterPublishing() {
        var same = org.updateDetails("chai", "the-1975", new EventDetails("The 1975 Bangkok", null, null, null, null, ""));
        assertNull(same.artwork());
        GatewayException e = assertThrows(GatewayException.class, () -> org.updateDetails("chai", "the-1975",
                new EventDetails(null, "UOB Live", null, null, null)));
        assertEquals("NOT_IN_CORE", e.code);
    }

    @Test
    void o07AndO10AreNotInCore() {
        String vip = org.getEvent("chai", "the-1975").tiers().get(0).tierId();
        assertEquals("NOT_IN_CORE", assertThrows(GatewayException.class,
                () -> org.updateTier("chai", "the-1975", vip, new TierInput(null, null, 600))).code);
        assertEquals("NOT_IN_CORE", assertThrows(GatewayException.class,
                () -> org.closeSale("chai", "the-1975")).code);
    }

    @Test
    void o09TheRateReachesTheQueue() {
        assertEquals(24, org.setRate("chai", "the-1975", 24).admitPerSecond());
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        assertEquals(24, gw.queueStatus("kenji", token).admitPerSecond());
        assertEquals("RATE_TOO_LOW", assertThrows(GatewayException.class,
                () -> org.setRate("chai", "the-1975", 0)).code);
    }

    @Test
    void o11TheDashboardAddsUpAndCountsPaidOrders() {
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        clock.advance(2_000);
        String vip = gw.availability("the-1975").get(0).tierId();
        var hold = gw.hold("kenji", token, vip, 2);
        var pay = gw.startPayment("kenji", hold.reservationId(), "promptpay");
        gw.answerPayment("kenji", pay.paymentId(), true);

        var d = org.dashboard("chai", "the-1975");
        assertEquals("ON_SALE", d.state());
        assertEquals(7500, d.all().total());
        assertEquals(2, d.all().sold());
        assertEquals(7498, d.all().remaining());
        assertEquals(13_000, d.revenue());
        for (var t : d.tiers()) {
            assertEquals(t.total(), t.held() + t.sold() + t.remaining());
        }
        assertTrue(d.closedAt() == null);
    }

    @Test
    void anotherOrganizersEventIsNotFound() {
        assertEquals(404, assertThrows(GatewayException.class, () -> org.getEvent("mint", "the-1975")).status);
    }
}
