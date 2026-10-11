package eventflow.web.real;

import eventflow.EventFlow;
import eventflow.contract.Money;
import eventflow.core.event.UserAdmitted;
import eventflow.core.inventory.TicketTier;
import eventflow.core.queue.QueueService;
import eventflow.web.GatewayException;
import eventflow.web.dto.Views.EventRef;
import eventflow.web.fake.DevControls;
import eventflow.web.fake.FakeBookingGateway;
import eventflow.web.fake.FakeClock;
import eventflow.web.fake.Scenario;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The real booking engine (core/) plus what the pages need that core does not keep yet:
 * drafts, pictures, the event's state, payments and orders. Shared by both core gateways.
 */
public final class RealEngine implements DevControls {

    static final ZoneId BANGKOK = FakeBookingGateway.BANGKOK;
    static final String NOT_IN_CORE = "NOT_IN_CORE";
    /** At most an hour of admission batches is caught up in one tick. */
    static final int MAX_BATCHES = 3600;

    final FakeClock clock;
    EventFlow flow;
    final Map<String, EventInfo> events = new LinkedHashMap<>();
    final Map<String, String> byCoreId = new HashMap<>();
    final Map<String, String> myTokens = new HashMap<>();
    final Map<String, HoldInfo> holds = new LinkedHashMap<>();
    final Map<String, PaymentInfo> payments = new LinkedHashMap<>();
    final Map<String, OrderInfo> orders = new LinkedHashMap<>();
    final Map<String, Integer> admitted = new HashMap<>();
    int paymentSeq;
    int orderSeq;

    static final class EventInfo {
        final String id;
        String name, shortName, venue, artwork, owner = "chai";
        long dateTime, saleOpensAt;
        int admitPerSecond = QueueService.DEFAULT_ADMISSION_RATE;
        final List<DraftTier> draftTiers = new ArrayList<>();
        String coreId;      // null while a draft: core has no drafts, so the event is created in core on publish
        boolean opened;
        long lastTick;

        EventInfo(String id) {
            this.id = id;
        }

        boolean draft() {
            return coreId == null;
        }
    }

    static final class DraftTier {
        final String id;
        String name;
        long price;
        int total;

        DraftTier(String id, String name, long price, int total) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.total = total;
        }
    }

    static final class HoldInfo {
        final String reservationId, customerId, eventId;
        final int before, after;
        String paymentId;

        HoldInfo(String reservationId, String customerId, String eventId, int before, int after) {
            this.reservationId = reservationId;
            this.customerId = customerId;
            this.eventId = eventId;
            this.before = before;
            this.after = after;
        }
    }

    static final class PaymentInfo {
        final String id, reservationId, customerId, providerKey;
        final long amount;
        String state = "PENDING";
        boolean answered;
        String orderId;

        PaymentInfo(String id, String reservationId, String customerId, String providerKey, long amount) {
            this.id = id;
            this.reservationId = reservationId;
            this.customerId = customerId;
            this.providerKey = providerKey;
            this.amount = amount;
        }
    }

    static final class OrderInfo {
        final String id, customerId, eventId, tierId, tierName, providerLabel;
        final int quantity;
        final long paid;
        final List<String> ticketIds = new ArrayList<>();

        OrderInfo(String id, String customerId, String eventId, String tierId, String tierName, int quantity,
                  long paid, String providerLabel) {
            this.id = id;
            this.customerId = customerId;
            this.eventId = eventId;
            this.tierId = tierId;
            this.tierName = tierName;
            this.quantity = quantity;
            this.paid = paid;
            this.providerLabel = providerLabel;
        }
    }

    public RealEngine(FakeClock clock) {
        this.clock = clock;
        seed();
    }

    // ---------- story data ----------

    synchronized void seed() {
        flow = new EventFlow(new EngineClock(clock));
        events.clear();
        byCoreId.clear();
        myTokens.clear();
        holds.clear();
        payments.clear();
        orders.clear();
        admitted.clear();
        paymentSeq = 5000;
        orderSeq = 9000;
        flow.bus().subscribe(UserAdmitted.class, a -> admitted.merge(a.eventId(), 1, Integer::sum));

        EventInfo e1975 = story("the-1975", "The 1975 Bangkok", "THE 1975", "Impact Arena",
                "assets/the-1975-card.png", at(2026, 11, 14, 19, 0), FakeBookingGateway.SALE_OPENS);
        e1975.draftTiers.add(new DraftTier("vip", "VIP", 6500, 500));
        e1975.draftTiers.add(new DraftTier("zone-a", "Zone A", 3800, 2000));
        e1975.draftTiers.add(new DraftTier("zone-b", "Zone B", 1900, 5000));
        publish(e1975);

        single("bruno-mars", "Bruno Mars", "BRUNO MARS", "Thunder Dome", "assets/bruno-mars.png",
                at(2026, 11, 22, 20, 0), at(2026, 10, 8, 10, 0), 1200, 3000);
        single("alan-walker", "Alan Walker", "ALAN WALKER", "UOB Live", "assets/alan-walker.png",
                at(2026, 12, 5, 20, 0), at(2026, 10, 15, 10, 0), 2400, 4000);
        single("marshmello", "Marshmello", "MARSHMELLO", "Moonstar Studio", "assets/marshmello.png",
                at(2026, 12, 18, 20, 0), at(2026, 10, 22, 10, 0), 1500, 2500);
    }

    private EventInfo story(String id, String name, String shortName, String venue, String art,
                            long date, long sale) {
        EventInfo e = new EventInfo(id);
        e.name = name;
        e.shortName = shortName;
        e.venue = venue;
        e.artwork = art;
        e.dateTime = date;
        e.saleOpensAt = sale;
        events.put(id, e);
        return e;
    }

    private void single(String id, String name, String shortName, String venue, String art,
                        long date, long sale, long price, int total) {
        EventInfo e = story(id, name, shortName, venue, art, date, sale);
        e.draftTiers.add(new DraftTier("standard", "Standard", price, total));
        publish(e);
    }

    static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(BANGKOK).toInstant().toEpochMilli();
    }

    // ---------- the gaps core leaves, filled here ----------

    /** Creates the event in core with its tiers and rate. Core has no drafts, so this happens on publish. */
    void publish(EventInfo e) {
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.dateTime), BANGKOK);
        LocalDateTime sale = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.saleOpensAt), BANGKOK);
        String coreId = flow.events().createEvent(e.name, e.venue, date.toLocalDate(), sale).eventId();
        for (DraftTier t : e.draftTiers) {
            flow.events().addTier(coreId, t.name, Money.baht(t.price), t.total);
        }
        flow.events().setAdmissionRate(coreId, e.admitPerSecond);
        e.draftTiers.clear();
        e.coreId = coreId;
        byCoreId.put(coreId, e.id);
    }

    /**
     * Brings core up to the demo clock: opens sales whose time has come (core's openSale opens at once),
     * lets one admission batch in per second passed, ends idle turns and expired holds.
     */
    void tick() {
        long now = clock.now();
        for (EventInfo e : events.values()) {
            if (e.draft()) {
                continue;
            }
            if (!e.opened && now >= e.saleOpensAt) {
                flow.events().openSale(e.coreId);
                e.opened = true;
                e.lastTick = e.saleOpensAt;
            }
            if (e.opened) {
                long seconds = Math.max(0, (now - e.lastTick) / 1000);
                for (long i = 0; i < Math.min(seconds, MAX_BATCHES); i++) {
                    flow.admission().admitBatch(e.coreId);
                }
                e.lastTick += seconds * 1000;
            }
        }
        flow.admission().expireIdle();
        flow.expiry().sweep();
    }

    /** Core only knows "on sale or not". DRAFT and SCHEDULED come from the gateway's own records. */
    String state(EventInfo e) {
        if (e.draft()) {
            return "DRAFT";
        }
        return clock.now() < e.saleOpensAt ? "SCHEDULED" : "ON_SALE";
    }

    EventInfo event(String id) {
        EventInfo e = events.get(id);
        if (e == null) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    /** An event a customer may know about: a draft does not exist yet. */
    EventInfo customerEvent(String id) {
        EventInfo e = event(id);
        if (e.draft()) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    EventInfo byCore(String coreId) {
        return events.get(byCoreId.get(coreId));
    }

    List<TicketTier> coreTiers(EventInfo e) {
        return e.draft() ? List.of() : flow.inventory().tiersOf(e.coreId);
    }

    EventRef ref(EventInfo e) {
        return new EventRef(e.id, e.name, e.shortName, e.venue, e.dateTime, e.artwork);
    }

    static GatewayException notInCore(String what) {
        return GatewayException.conflict(NOT_IN_CORE, null, what + " is not built in the booking engine yet.", null);
    }

    // ---------- demo controls ----------

    @Override
    public synchronized void scenario(Scenario scenario) {
        throw GatewayException.badRequest(NOT_IN_CORE, "Scenarios only work on the fake engine.");
    }

    @Override
    public synchronized void speed(int times) {
        if (times != 0 && times != 1 && times != 10 && times != 30 && times != 100) {
            throw GatewayException.badRequest("BAD_SPEED", "Speed must be 0, 1, 10, 30 or 100");
        }
        clock.setSpeed(times);
    }

    @Override
    public synchronized void advance(long seconds) {
        clock.advance(seconds * 1000);
    }

    @Override
    public synchronized void reset() {
        clock.setSpeed(1);
        clock.set(FakeBookingGateway.START);
        seed();
    }
}
