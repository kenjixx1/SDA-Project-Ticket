package eventflow.web.fake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import eventflow.web.GatewayException;
import eventflow.web.dto.OrgRequests.EventDetails;
import eventflow.web.dto.OrgRequests.TierInput;
import eventflow.web.dto.OrgViews.Dashboard;
import eventflow.web.dto.OrgViews.OrgEvent;
import eventflow.web.dto.OrgViews.OrgEventRow;
import eventflow.web.dto.OrgViews.OrgTier;
import eventflow.web.dto.Views.EventSummary;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.dto.Views.TierAvailability;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeOrganizerTest {

    private static final long EVENT = FakeBookingGateway.at(2026, 11, 14, 19, 0);
    private static final long SALE = FakeBookingGateway.at(2026, 11, 1, 10, 0);

    private FakeClock clock;
    private FakeBookingGateway gw;
    private FakeOrganizerGateway org;

    @BeforeEach
    void setUp() {
        AtomicLong real = new AtomicLong(0);
        clock = new FakeClock(FakeBookingGateway.START, real::get);
        gw = new FakeBookingGateway(clock);
        org = new FakeOrganizerGateway(gw);
    }

    private static EventDetails details(String name) {
        return new EventDetails(name, "Impact Arena", EVENT, SALE, 12);
    }

    private static TierInput tier(String name, long price, int total) {
        return new TierInput(name, price, total);
    }

    private static OrgTier row(OrgEvent event, String tierId) {
        return event.tiers().stream().filter(t -> t.tierId().equals(tierId)).findFirst().orElseThrow();
    }

    private static void assertLocked(GatewayException e) {
        assertEquals(409, e.status);
        assertEquals("EVENT_LOCKED", e.code);
        assertNull(e.field);
        assertEquals("This event can no longer be changed.", e.getMessage());
    }

    @Test
    void listsChaisFourEventsNewestDateFirst() {
        List<OrgEventRow> rows = org.listEvents("chai");

        assertEquals(List.of("marshmello", "alan-walker", "bruno-mars", "the-1975"),
                rows.stream().map(OrgEventRow::eventId).toList());

        OrgEventRow marshmello = rows.get(0);
        assertEquals("Marshmello", marshmello.name());
        assertEquals("Moonstar Studio", marshmello.venue());
        assertEquals(0, marshmello.sold());
        assertEquals(2500, marshmello.total());

        OrgEventRow the1975 = rows.get(3);
        assertEquals("The 1975 Bangkok", the1975.name());
        assertEquals("Impact Arena", the1975.venue());
        assertEquals(EVENT, the1975.eventDateTime());
        assertEquals(FakeBookingGateway.SALE_OPENS, the1975.saleOpensAt());
        assertEquals("SCHEDULED", the1975.state());
        assertEquals(5114, the1975.sold());
        assertEquals(7500, the1975.total());
    }

    @Test
    void otherOrganizerSeesNothing() {
        assertEquals(List.of(), org.listEvents("someone"));

        GatewayException e = assertThrows(GatewayException.class, () -> org.getEvent("someone", "the-1975"));
        assertEquals(404, e.status);
        assertEquals("NOT_FOUND", e.code);
    }

    @Test
    void createMakesADraftHiddenFromCustomers() {
        OrgEvent created = org.createEvent("chai", details("Test Night Bangkok"));

        assertEquals("test-night-bangkok", created.eventId());
        assertEquals("Test Night Bangkok", created.name());
        assertEquals("Impact Arena", created.venue());
        assertEquals("DRAFT", created.state());
        assertEquals(12, created.admitPerSecond());
        assertEquals(List.of(), created.tiers());
        assertEquals(0, created.heldNow());
        assertEquals(0, created.waiting());
        assertNull(created.closedAt());

        assertTrue(gw.listEvents("kenji").stream().noneMatch(s -> s.eventId().equals("test-night-bangkok")));

        GatewayException e = assertThrows(GatewayException.class, () -> gw.getEvent("kenji", "test-night-bangkok"));
        assertEquals(404, e.status);
        assertEquals("NOT_FOUND", e.code);
    }

    @Test
    void createRefusesASaleInThePast() {
        GatewayException e = assertThrows(GatewayException.class, () -> org.createEvent("chai",
                new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, FakeBookingGateway.START - 1, 12)));

        assertEquals(400, e.status);
        assertEquals("SALE_IN_PAST", e.code);
        assertEquals("saleOpensAt", e.field);
        assertEquals("The sale must open in the future.", e.getMessage());
    }

    @Test
    void createRefusesASaleAfterTheEvent() {
        GatewayException e = assertThrows(GatewayException.class, () -> org.createEvent("chai",
                new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, EVENT + 1, 12)));

        assertEquals(400, e.status);
        assertEquals("SALE_AFTER_EVENT", e.code);
        assertEquals("saleOpensAt", e.field);
        assertEquals("The sale must open before the event.", e.getMessage());
    }

    @Test
    void createRefusesEmptyAndTooLong() {
        GatewayException missing = assertThrows(GatewayException.class, () -> org.createEvent("chai", details("")));
        assertEquals(400, missing.status);
        assertEquals("REQUIRED", missing.code);
        assertEquals("name", missing.field);
        assertEquals("Required", missing.getMessage());

        GatewayException longName = assertThrows(GatewayException.class,
                () -> org.createEvent("chai", details("x".repeat(41))));
        assertEquals(400, longName.status);
        assertEquals("TOO_LONG", longName.code);
        assertEquals("name", longName.field);
        assertEquals("Up to 40 characters", longName.getMessage());

        GatewayException longVenue = assertThrows(GatewayException.class, () -> org.createEvent("chai",
                new EventDetails("Test Night Bangkok", "y".repeat(31), EVENT, SALE, 12)));
        assertEquals(400, longVenue.status);
        assertEquals("TOO_LONG", longVenue.code);
        assertEquals("venue", longVenue.field);
        assertEquals("Up to 30 characters", longVenue.getMessage());
    }

    @Test
    void createRefusesRateBelowOne() {
        GatewayException e = assertThrows(GatewayException.class, () -> org.createEvent("chai",
                new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, 0)));

        assertEquals(400, e.status);
        assertEquals("RATE_TOO_LOW", e.code);
        assertEquals("admitPerSecond", e.field);
        assertEquals("Must be at least 1.", e.getMessage());
    }

    @Test
    void sameNameGetsANewId() {
        assertEquals("test-night-bangkok", org.createEvent("chai", details("Test Night Bangkok")).eventId());
        assertEquals("test-night-bangkok-2", org.createEvent("chai", details("Test Night Bangkok")).eventId());
    }

    @Test
    void replaceTiersValidatesEachRow() {
        String id = org.createEvent("chai", details("Test Night Bangkok")).eventId();

        GatewayException seats = assertThrows(GatewayException.class, () -> org.replaceTiers("chai", id,
                List.of(tier("VIP", 6500, 500), tier("Zone B", 1900, 0))));
        assertEquals(400, seats.status);
        assertEquals("SEATS_ZERO", seats.code);
        assertEquals("tiers[1].total", seats.field);
        assertEquals("Zone B · Seats must be more than 0.", seats.getMessage());

        GatewayException price = assertThrows(GatewayException.class,
                () -> org.replaceTiers("chai", id, List.of(tier("VIP", -1, 500))));
        assertEquals(400, price.status);
        assertEquals("PRICE_NEGATIVE", price.code);
        assertEquals("tiers[0].price", price.field);
        assertEquals("VIP · Price cannot be below ฿0.", price.getMessage());

        GatewayException name = assertThrows(GatewayException.class,
                () -> org.replaceTiers("chai", id, List.of(tier("", 100, 5))));
        assertEquals(400, name.status);
        assertEquals("TIER_NAME_REQUIRED", name.code);
        assertEquals("tiers[0].name", name.field);
        assertEquals("Tier 1 · Name is required.", name.getMessage());

        List<TierInput> nine = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            nine.add(tier("Tier " + i, 500, 10));
        }
        GatewayException many = assertThrows(GatewayException.class, () -> org.replaceTiers("chai", id, nine));
        assertEquals(400, many.status);
        assertEquals("TOO_MANY_TIERS", many.code);
        assertEquals("tiers", many.field);
        assertEquals("Up to 8 tiers.", many.getMessage());
    }

    @Test
    void replaceTiersSetsIdsFromNames() {
        String id = org.createEvent("chai", details("Test Night Bangkok")).eventId();
        OrgEvent filled = org.replaceTiers("chai", id,
                List.of(tier("VIP", 6500, 500), tier("Zone A", 3800, 2000), tier("Zone B", 1900, 5000)));

        assertEquals(List.of("vip", "zone-a", "zone-b"), filled.tiers().stream().map(OrgTier::tierId).toList());
        assertEquals(List.of("VIP", "Zone A", "Zone B"), filled.tiers().stream().map(OrgTier::name).toList());

        OrgTier vip = row(filled, "vip");
        assertEquals(6500, vip.price());
        assertEquals(500, vip.total());
        assertEquals(0, vip.held());
        assertEquals(0, vip.sold());
        assertEquals(500, vip.remaining());
    }

    @Test
    void publishNeedsATier() {
        String id = org.createEvent("chai", details("Test Night Bangkok")).eventId();

        GatewayException e = assertThrows(GatewayException.class, () -> org.publish("chai", id));
        assertEquals(409, e.status);
        assertEquals("NO_TIERS", e.code);
        assertNull(e.field);
        assertEquals("An event needs at least one tier before it can be published.", e.getMessage());
    }

    @Test
    void publishMakesItScheduledAndVisible() {
        String id = org.createEvent("chai", details("Test Night Bangkok")).eventId();
        org.replaceTiers("chai", id, List.of(tier("VIP", 6500, 500)));

        assertEquals("SCHEDULED", org.publish("chai", id).state());

        EventSummary seen = gw.listEvents("kenji").stream()
                .filter(s -> s.eventId().equals("test-night-bangkok")).findFirst().orElseThrow();
        assertEquals("SCHEDULED", seen.state());
        assertEquals("Test Night Bangkok", seen.name());
        assertEquals(6500, seen.fromPrice());
    }

    @Test
    void publishTwiceIsRefused() {
        String id = org.createEvent("chai", details("Test Night Bangkok")).eventId();
        org.replaceTiers("chai", id, List.of(tier("VIP", 6500, 500)));
        org.publish("chai", id);

        GatewayException e = assertThrows(GatewayException.class, () -> org.publish("chai", id));
        assertEquals(409, e.status);
        assertEquals("NOT_DRAFT", e.code);
        assertNull(e.field);
        assertEquals("Only a draft can do this.", e.getMessage());
    }

    @Test
    void replaceTiersOnlyOnADraft() {
        GatewayException e = assertThrows(GatewayException.class,
                () -> org.replaceTiers("chai", "the-1975", List.of(tier("VIP", 6500, 500))));
        assertEquals(409, e.status);
        assertEquals("NOT_DRAFT", e.code);
        assertEquals("Only a draft can do this.", e.getMessage());
    }

    @Test
    void priceIsFrozenWhileOnSale() {
        clock.advance(60_000);

        GatewayException e = assertThrows(GatewayException.class,
                () -> org.updateTier("chai", "the-1975", "vip", new TierInput("VIP", 7200L, 500)));
        assertEquals(409, e.status);
        assertEquals("PRICE_FROZEN", e.code);
        assertEquals("price", e.field);
        assertNull(e.remaining);
        assertEquals("Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.",
                e.getMessage());
    }

    @Test
    void samePriceWhileOnSaleIsFine() {
        clock.advance(60_000);

        OrgEvent updated = org.updateTier("chai", "the-1975", "vip", new TierInput("VIP", 6500L, 600));
        OrgTier vip = row(updated, "vip");
        assertEquals(6500, vip.price());
        assertEquals(600, vip.total());
        assertEquals(116, vip.remaining());

        TierAvailability seen = gw.availability("the-1975").stream()
                .filter(t -> t.tierId().equals("vip")).findFirst().orElseThrow();
        assertEquals(600, seen.total());
        assertEquals(116, seen.remaining());
    }

    @Test
    void cannotReduceBelowHeldPlusSold() {
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        clock.advance(30_000);
        gw.hold("kenji", token, "vip", 2);

        OrgTier vip = row(org.getEvent("chai", "the-1975"), "vip");
        assertEquals(484, vip.sold());
        assertEquals(2, vip.held());

        GatewayException e = assertThrows(GatewayException.class,
                () -> org.updateTier("chai", "the-1975", "vip", new TierInput("VIP", 6500L, 485)));
        assertEquals(409, e.status);
        assertEquals("BELOW_HELD_SOLD", e.code);
        assertEquals("total", e.field);
        assertEquals(486, e.remaining);
        assertEquals("VIP: 484 sold and 2 held. Cannot reduce below 486.", e.getMessage());
    }

    @Test
    void priceCanChangeBeforeTheSale() {
        OrgTier vip = row(org.updateTier("chai", "the-1975", "vip", new TierInput("VIP", 7000L, null)), "vip");

        assertEquals(7000, vip.price());
        assertEquals(500, vip.total());
    }

    @Test
    void addTierWhileOnSaleIsBuyable() {
        clock.advance(60_000);

        OrgEvent updated = org.addTier("chai", "the-1975", tier("Zone C", 990, 100));
        OrgTier zoneC = row(updated, "zone-c");
        assertEquals(990, zoneC.price());
        assertEquals(100, zoneC.total());
        assertEquals(0, zoneC.held());
        assertEquals(0, zoneC.sold());
        assertEquals(100, zoneC.remaining());

        TierAvailability seen = gw.availability("the-1975").stream()
                .filter(t -> t.tierId().equals("zone-c")).findFirst().orElseThrow();
        assertEquals(990, seen.price());
        assertEquals(100, seen.remaining());
    }

    @Test
    void rateChangesTheQueue() {
        assertEquals(24, org.setRate("chai", "the-1975", 24).admitPerSecond());

        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        assertEquals(24, gw.queueStatus("kenji", token).admitPerSecond());

        GatewayException e = assertThrows(GatewayException.class, () -> org.setRate("chai", "the-1975", 0));
        assertEquals(400, e.status);
        assertEquals("RATE_TOO_LOW", e.code);
        assertEquals("admitPerSecond", e.field);
        assertEquals("Must be at least 1.", e.getMessage());
    }

    @Test
    void closeSaleKeepsLiveHolds() {
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        clock.advance(30_000);
        HoldView hold = gw.hold("kenji", token, "vip", 2);

        OrgEvent closing = org.closeSale("chai", "the-1975");
        assertEquals("CLOSING", closing.state());
        assertEquals(2, closing.heldNow());
        assertNull(closing.closedAt());
        assertEquals("CLOSED", gw.getEvent("kenji", "the-1975").state());
        assertEquals("HELD", gw.getHold("kenji", hold.reservationId()).state());

        clock.advance(601_000);
        OrgEvent closed = org.getEvent("chai", "the-1975");
        assertEquals("CLOSED", closed.state());
        assertEquals(0, closed.heldNow());
        assertNotNull(closed.closedAt());
    }

    @Test
    void closeWithNoHoldsIsClosedAtOnce() {
        clock.advance(60_000);

        OrgEvent closed = org.closeSale("chai", "the-1975");
        assertEquals("CLOSED", closed.state());
        assertEquals(0, closed.heldNow());
        assertNotNull(closed.closedAt());
    }

    @Test
    void closeOnlyWhileOnSale() {
        GatewayException e = assertThrows(GatewayException.class, () -> org.closeSale("chai", "the-1975"));
        assertEquals(409, e.status);
        assertEquals("NOT_ON_SALE", e.code);
        assertNull(e.field);
        assertEquals("Only a sale that is on can be closed.", e.getMessage());
    }

    @Test
    void closedEventIsLocked() {
        clock.advance(60_000);
        org.closeSale("chai", "the-1975");

        GatewayException details = assertThrows(GatewayException.class, () -> org.updateDetails("chai", "the-1975",
                new EventDetails("The 1975 Bangkok", "UOB Live", EVENT, FakeBookingGateway.SALE_OPENS, 12)));
        assertLocked(details);

        GatewayException added = assertThrows(GatewayException.class,
                () -> org.addTier("chai", "the-1975", tier("Zone C", 990, 100)));
        assertLocked(added);

        GatewayException updated = assertThrows(GatewayException.class,
                () -> org.updateTier("chai", "the-1975", "vip", new TierInput("VIP", 6500L, 600)));
        assertLocked(updated);

        GatewayException rate = assertThrows(GatewayException.class, () -> org.setRate("chai", "the-1975", 24));
        assertLocked(rate);
    }

    @Test
    void saleTimeLockedOnceOnSale() {
        clock.advance(60_000);

        GatewayException e = assertThrows(GatewayException.class, () -> org.updateDetails("chai", "the-1975",
                new EventDetails("The 1975 Bangkok", "Impact Arena", EVENT, FakeBookingGateway.SALE_OPENS + 1, 12)));
        assertEquals(409, e.status);
        assertEquals("EVENT_LOCKED", e.code);
        assertEquals("saleOpensAt", e.field);
        assertEquals("The sale time cannot change once the sale is on.", e.getMessage());

        OrgEvent updated = org.updateDetails("chai", "the-1975",
                new EventDetails(null, "UOB Live", null, FakeBookingGateway.SALE_OPENS, null));
        assertEquals("UOB Live", updated.venue());
        assertEquals("The 1975 Bangkok", updated.name());
        assertEquals(FakeBookingGateway.SALE_OPENS, updated.saleOpensAt());
        assertEquals(200, updated.admitPerSecond());
    }

    @Test
    void closingStopsNewAdmissions() {
        clock.advance(60_000);
        String mint = gw.joinQueue("mint", "the-1975").token();
        String kenji = gw.joinQueue("kenji", "the-1975").token();
        clock.advance(30_000);
        gw.hold("kenji", kenji, "vip", 2);

        assertEquals("CLOSING", org.closeSale("chai", "the-1975").state());

        clock.advance(600_000);
        assertEquals("WAITING", gw.queueStatus("mint", mint).state());
    }

    @Test
    void dashboardAddsUp() {
        clock.advance(90_000); // 10:01:00, 60 seconds after the sale opened

        Dashboard d = org.dashboard("chai", "the-1975");
        assertEquals("the-1975", d.eventId());
        assertEquals("The 1975 Bangkok", d.name());
        assertEquals("ON_SALE", d.state());
        assertEquals(23_768, d.waiting());
        assertEquals(200, d.admitPerSecond());
        assertNull(d.closedAt());
        assertEquals(3, d.tiers().size());

        for (OrgTier t : d.tiers()) {
            assertEquals(t.total(), t.held() + t.sold() + t.remaining());
        }

        OrgTier all = d.all();
        assertEquals("all", all.tierId());
        assertEquals("All", all.name());
        assertEquals(0, all.price());
        assertEquals(7500, all.total());
        assertEquals(0, all.held());
        assertEquals(5114, all.sold());
        assertEquals(2386, all.remaining());

        assertEquals(484L * 6500 + 1650L * 3800 + 2980L * 1900, d.revenue());
        assertEquals(15_078_000L, d.revenue());
    }

    @Test
    void dashboardRevenueCountsPaidOrders() {
        clock.advance(60_000);
        String token = gw.joinQueue("kenji", "the-1975").token();
        clock.advance(30_000);
        HoldView hold = gw.hold("kenji", token, "vip", 1);
        var payment = gw.startPayment("kenji", hold.reservationId(), "promptpay");
        gw.answerPayment("kenji", payment.paymentId(), true);
        clock.advance(2_000);

        long before = 484L * 6500 + 1650L * 3800 + 2980L * 1900;
        assertEquals(1, gw.orders("kenji").get(0).quantity());
        assertEquals(5850, gw.orders("kenji").get(0).paidAmount());
        assertEquals(before + 5850, org.dashboard("chai", "the-1975").revenue());
    }

    @Test
    void updateTierTrimsTheName() {
        OrgEvent e = org.updateTier("chai", "the-1975", "vip", new TierInput(" VIP ", null, null));
        assertEquals("VIP", row(e, "vip").name());
    }

    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    @Test
    void pictureIsTheCustomersCard() {
        OrgEvent made = org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, 12, PNG));
        assertEquals(PNG, made.artwork());
        org.replaceTiers("chai", made.eventId(), List.of(tier("VIP", 6500, 500)));
        org.publish("chai", made.eventId());
        EventSummary seen = gw.listEvents("kenji").stream().filter(x -> x.eventId().equals(made.eventId())).findFirst().orElseThrow();
        assertEquals(PNG, seen.artwork());
    }

    @Test
    void pictureMustBeAnImage() {
        GatewayException e = assertThrows(GatewayException.class, () ->
                org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, 12, "data:text/html;base64,PGI+")));
        assertEquals("BAD_IMAGE", e.code);
        assertEquals("artwork", e.field);
        assertEquals("Use a PNG, JPG or WebP picture of 2 MB or less.", e.getMessage());
    }

    @Test
    void emptyPictureRemovesItAndNullKeepsIt() {
        OrgEvent made = org.createEvent("chai", new EventDetails("Test Night Bangkok", "Impact Arena", EVENT, SALE, 12, PNG));
        assertEquals(PNG, org.updateDetails("chai", made.eventId(), new EventDetails(null, "UOB Live", null, null, null)).artwork());
        assertNull(org.updateDetails("chai", made.eventId(), new EventDetails(null, null, null, null, null, "")).artwork());
    }
}
