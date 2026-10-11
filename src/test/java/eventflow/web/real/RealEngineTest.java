package eventflow.web.real;

import eventflow.web.GatewayException;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import eventflow.web.fake.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealEngineTest {

    private FakeClock clock;
    private RealEngine engine;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        engine = new RealEngine(clock);
    }

    @Test
    void seedPublishesTheFourStoryEventsIntoCore() {
        assertEquals(4, engine.events.size());
        RealEngine.EventInfo e = engine.event("the-1975");
        assertEquals("The 1975 Bangkok", e.name);
        assertEquals("chai", e.owner);
        assertEquals(3, engine.coreTiers(e).size());
        assertEquals(7500, engine.coreTiers(e).stream().mapToInt(t -> t.totalQuantity()).sum());
        assertEquals("SCHEDULED", engine.state(e));
        assertEquals(200, engine.flow.queue().admissionRate(e.coreId));
    }

    @Test
    void theSaleOpensAtTenAndTheQueueLetsPeopleIn() {
        clock.advance(31_000); // 10:00:01
        engine.tick();
        RealEngine.EventInfo e = engine.event("the-1975");
        assertEquals("ON_SALE", engine.state(e));
        var join = engine.flow.queue().join("kenji", e.coreId);
        assertTrue(join.hasToken());
        clock.advance(1_000);
        engine.tick();
        assertEquals("ADMITTED", engine.flow.queue().token(join.token().tokenId()).state().name());
        assertEquals(1, engine.admitted.get(e.coreId));
    }

    @Test
    void scenariosAreRefused() {
        GatewayException e = assertThrows(GatewayException.class, () -> engine.scenario(Scenario.RACE_LOST));
        assertEquals(400, e.status);
        assertEquals("NOT_IN_CORE", e.code);
    }

    @Test
    void resetBuildsAFreshEngineAndPutsTheClockBack() {
        var old = engine.flow;
        engine.speed(100);
        engine.advance(500);
        engine.reset();
        assertEquals(FakeBookingGateway.START, clock.now());
        assertEquals(1, clock.speed());
        assertTrue(old != engine.flow);
        assertEquals(4, engine.events.size());
    }

    @Test
    void speedOnlyAcceptsTheAllowedValues() {
        assertThrows(GatewayException.class, () -> engine.speed(7));
    }

    @Test
    void notInCoreIsA409() {
        GatewayException e = RealEngine.notInCore("Closing a sale");
        assertEquals(409, e.status);
        assertEquals("NOT_IN_CORE", e.code);
        assertEquals("Closing a sale is not built in the booking engine yet.", e.getMessage());
    }
}
