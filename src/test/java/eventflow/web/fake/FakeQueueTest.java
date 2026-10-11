package eventflow.web.fake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import eventflow.web.GatewayException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeQueueTest {

    private FakeClock clock;
    private FakeBookingGateway gw;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new FakeBookingGateway(clock);
    }

    @Test
    void listsFourEventsSoonestSaleFirst() {
        var events = gw.listEvents("kenji");
        assertEquals(4, events.size());
        assertEquals("the-1975", events.get(0).eventId());
        assertEquals("SCHEDULED", events.get(0).state());
        assertEquals(1900, events.get(0).fromPrice());
        assertEquals("Impact Arena", events.get(0).venue());
    }

    @Test
    void cannotJoinBeforeTheSaleOpens() {
        var e = assertThrows(GatewayException.class, () -> gw.joinQueue("kenji", "the-1975"));
        assertEquals("NOT_ON_SALE", e.code);
        assertEquals(400, e.status);
    }

    @Test
    void joinGivesAPlaceAndJoiningAgainGivesTheSamePlace() {
        clock.advance(60_000);
        var first = gw.joinQueue("kenji", "the-1975");
        assertEquals(FakeBookingGateway.START_AHEAD, first.position());
        assertFalse(first.isExisting());

        var again = gw.joinQueue("kenji", "the-1975");
        assertEquals(first.token(), again.token());
        assertTrue(again.isExisting());

        var detail = gw.getEvent("kenji", "the-1975");
        assertEquals(first.token(), detail.myToken());
        assertEquals("ON_SALE", detail.state());
        assertNull(gw.getEvent("mint", "the-1975").myToken());
    }

    @Test
    void positionMovesAtTheAdmissionRateUntilAdmitted() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");

        clock.advance(10_000);
        var q = gw.queueStatus("kenji", join.token());
        assertEquals("WAITING", q.state());
        assertEquals(4182 - 120, q.position());
        assertEquals(6823 + 120, q.moved());

        clock.advance(340_000);
        assertEquals("ADMITTED", gw.queueStatus("kenji", join.token()).state());
    }

    @Test
    void admittedTurnPassesAfterFiveMinutes() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");
        clock.advance(350_000 + FakeBookingGateway.TURN_LIMIT_MS + 1_000);
        assertEquals("EXPIRED", gw.queueStatus("kenji", join.token()).state());
    }

    @Test
    void turnPassedScenarioEndsThePlaceNow() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");
        gw.scenario(Scenario.TURN_PASSED);
        assertEquals("EXPIRED", gw.queueStatus("kenji", join.token()).state());
    }

    @Test
    void anotherCustomersTokenIsNotFound() {
        clock.advance(60_000);
        var join = gw.joinQueue("kenji", "the-1975");
        var e = assertThrows(GatewayException.class, () -> gw.queueStatus("mint", join.token()));
        assertEquals(404, e.status);
    }

    @Test
    void cancelledEventLeavesTheList() {
        gw.scenario(Scenario.EVENT_CANCELLED);
        assertEquals(3, gw.listEvents("kenji").size());
        assertEquals("CANCELLED", gw.getEvent("kenji", "the-1975").state());
    }

    @Test
    void resetPutsTheClockBack() {
        gw.speed(100);
        gw.advance(500);
        gw.reset();
        assertEquals(FakeBookingGateway.START, clock.now());
        assertEquals(1, clock.speed());
    }

    @Test
    void speedOnlyAcceptsTheAllowedValues() {
        assertThrows(GatewayException.class, () -> gw.speed(7));
    }
}
