package th.ac.kmitl.sda.ticketqueue.web.fake;

import th.ac.kmitl.sda.ticketqueue.web.BookingGateway;
import th.ac.kmitl.sda.ticketqueue.web.GatewayException;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventDetail;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventRef;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.EventSummary;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.HoldView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.JoinResult;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.Me;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.OrderView;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentStart;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.PaymentStatus;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.QueueStatus;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.TierAvailability;
import th.ac.kmitl.sda.ticketqueue.web.dto.Views.TierPrice;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory stand-in for core/. Just enough rules to drive every customer screen.
 * Throw-away: replaced by CoreBookingGateway when core/ is ready.
 */
public class FakeBookingGateway implements BookingGateway, DevControls {

    public static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    public static final long SALE_OPENS = at(2026, 10, 1, 10, 0);
    public static final long START = SALE_OPENS - 30_000;           // 09:59:30, so SCR-05a shows first
    public static final long HOLD_MS = 10 * 60_000L;
    public static final int ORDER_LIMIT = 4;
    public static final int START_AHEAD = 4182;
    public static final int START_MOVED = 6823;
    public static final int ADMIT_PER_SECOND = 12;
    public static final long TURN_LIMIT_MS = 5 * 60_000L;
    static final long ADMIT_AFTER_MS = (long) Math.ceil(START_AHEAD * 1000.0 / ADMIT_PER_SECOND);

    final FakeClock clock;
    final Set<Scenario> scenarios = EnumSet.noneOf(Scenario.class);
    final Map<String, Event> events = new LinkedHashMap<>();
    final Map<String, Token> tokens = new LinkedHashMap<>();
    int tokenSeq;

    public FakeBookingGateway(FakeClock clock) {
        this.clock = clock;
        seed();
    }

    static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, BANGKOK).toInstant().toEpochMilli();
    }

    // ---------- data ----------

    static final class Event {
        final String id, name, shortName, venue, artwork;
        final long dateTime, saleOpensAt;
        final List<Tier> tiers = new ArrayList<>();
        boolean cancelled, closed;

        Event(String id, String name, String shortName, String venue, String artwork, long dateTime, long saleOpensAt) {
            this.id = id;
            this.name = name;
            this.shortName = shortName;
            this.venue = venue;
            this.artwork = artwork;
            this.dateTime = dateTime;
            this.saleOpensAt = saleOpensAt;
        }

        Tier tier(String tierId) {
            return tiers.stream().filter(t -> t.id.equals(tierId)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Tier"));
        }

        boolean soldOut() {
            return tiers.stream().allMatch(t -> t.remaining() == 0);
        }

        EventRef ref() {
            return new EventRef(id, name, shortName, venue, dateTime);
        }
    }

    static final class Tier {
        final String id, name;
        final long price;
        final int total;
        int held, sold;

        Tier(String id, String name, long price, int total, int sold) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.total = total;
            this.sold = sold;
        }

        int remaining() {
            return total - held - sold;
        }
    }

    static final class Token {
        final String id, eventId, customerId;
        final long issuedAt;
        String state = "WAITING";
        long admittedAt;

        Token(String id, String eventId, String customerId, long issuedAt) {
            this.id = id;
            this.eventId = eventId;
            this.customerId = customerId;
            this.issuedAt = issuedAt;
        }
    }

    void seed() {
        events.clear();
        tokens.clear();
        scenarios.clear();
        tokenSeq = 1000;

        Event e1975 = new Event("the-1975", "The 1975 Bangkok", "THE 1975", "Impact Arena",
                "assets/the-1975-card.png", at(2026, 11, 14, 19, 0), SALE_OPENS);
        e1975.tiers.add(new Tier("vip", "VIP", 6500, 500, 484));
        e1975.tiers.add(new Tier("zone-a", "Zone A", 3800, 2000, 1650));
        e1975.tiers.add(new Tier("zone-b", "Zone B", 1900, 5000, 2980));
        add(e1975);

        add(single("bruno-mars", "Bruno Mars", "BRUNO MARS", "Thunder Dome", "assets/bruno-mars.png",
                at(2026, 11, 22, 20, 0), at(2026, 10, 8, 10, 0), 1200, 3000));
        add(single("alan-walker", "Alan Walker", "ALAN WALKER", "UOB Live", "assets/alan-walker.png",
                at(2026, 12, 5, 20, 0), at(2026, 10, 15, 10, 0), 2400, 4000));
        add(single("marshmello", "Marshmello", "MARSHMELLO", "Moonstar Studio", "assets/marshmello.png",
                at(2026, 12, 18, 20, 0), at(2026, 10, 22, 10, 0), 1500, 2500));
        seedOrders();
    }

    /** Task 3 fills this in with reservations, payments and Kenji's past order. */
    void seedOrders() {
    }

    private static Event single(String id, String name, String shortName, String venue, String art,
                                long date, long sale, long price, int total) {
        Event e = new Event(id, name, shortName, venue, art, date, sale);
        e.tiers.add(new Tier("standard", "Standard", price, total, 0));
        return e;
    }

    private void add(Event e) {
        events.put(e.id, e);
    }

    Event event(String eventId) {
        Event e = events.get(eventId);
        if (e == null) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    String state(Event e) {
        if (e.cancelled) {
            return "CANCELLED";
        }
        if (e.closed) {
            return "CLOSED";
        }
        return clock.now() < e.saleOpensAt ? "SCHEDULED" : "ON_SALE";
    }

    Token ownedToken(String customerId, String tokenId) {
        Token t = tokens.get(tokenId);
        if (t == null || !t.customerId.equals(customerId)) {
            throw GatewayException.notFound("Place in line");
        }
        refresh(t);
        return t;
    }

    void refresh(Token t) {
        long now = clock.now();
        if (t.state.equals("WAITING") && now >= t.issuedAt + ADMIT_AFTER_MS) {
            t.state = "ADMITTED";
            t.admittedAt = t.issuedAt + ADMIT_AFTER_MS;
        }
        if (t.state.equals("ADMITTED") && now - t.admittedAt > TURN_LIMIT_MS) {
            t.state = "EXPIRED";
        }
    }

    int admittedSoFar(Token t) {
        long elapsed = Math.max(0, clock.now() - t.issuedAt);
        return (int) Math.min(START_AHEAD, elapsed * ADMIT_PER_SECOND / 1000);
    }

    int position(Token t) {
        return t.state.equals("WAITING") ? Math.max(1, START_AHEAD - admittedSoFar(t)) : 0;
    }

    /** Task 3 overrides the body: expires holds and settles payments that are due. */
    void sweep() {
    }

    // ---------- M-01 to M-04, M-13 ----------

    @Override
    public synchronized long now() {
        return clock.now();
    }

    @Override
    public synchronized int clockRate() {
        return clock.speed();
    }

    @Override
    public synchronized List<EventSummary> listEvents(String customerId) {
        sweep();
        return events.values().stream()
                .filter(e -> !e.cancelled)
                .sorted(Comparator.comparingLong(e -> e.saleOpensAt))
                .map(e -> new EventSummary(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                        state(e), e.tiers.stream().mapToLong(t -> t.price).min().orElse(0), e.artwork, e.soldOut()))
                .toList();
    }

    @Override
    public synchronized EventDetail getEvent(String customerId, String eventId) {
        sweep();
        Event e = event(eventId);
        Token mine = activeToken(customerId, eventId);
        return new EventDetail(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt, state(e),
                e.artwork, e.soldOut(),
                e.tiers.stream().map(t -> new TierPrice(t.id, t.name, t.price)).toList(),
                mine == null ? null : mine.id,
                mine == null || !mine.state.equals("WAITING") ? null : position(mine));
    }

    private Token activeToken(String customerId, String eventId) {
        Token found = null;
        for (Token t : tokens.values()) {
            if (t.customerId.equals(customerId) && t.eventId.equals(eventId)) {
                refresh(t);
                if (t.state.equals("WAITING") || t.state.equals("ADMITTED")) {
                    found = t;
                }
            }
        }
        return found;
    }

    @Override
    public synchronized JoinResult joinQueue(String customerId, String eventId) {
        Event e = event(eventId);
        if (!state(e).equals("ON_SALE")) {
            throw GatewayException.badRequest("NOT_ON_SALE", "This sale is not open");
        }
        Token existing = activeToken(customerId, eventId);
        if (existing != null) {
            return new JoinResult(existing.id, eventId, position(existing), true);
        }
        Token t = new Token("Q-" + (++tokenSeq), eventId, customerId, clock.now());
        tokens.put(t.id, t);
        return new JoinResult(t.id, eventId, position(t), false);
    }

    @Override
    public synchronized QueueStatus queueStatus(String customerId, String tokenId) {
        Token t = ownedToken(customerId, tokenId);
        if (scenarios.remove(Scenario.TURN_PASSED)
                && (t.state.equals("WAITING") || t.state.equals("ADMITTED"))) {
            t.state = "EXPIRED";
        }
        return new QueueStatus(t.id, t.eventId, t.state, position(t), START_MOVED + admittedSoFar(t),
                ADMIT_PER_SECOND);
    }

    @Override
    public synchronized Me me(String customerId) {
        if (customerId.equals("kenji")) {
            return new Me(customerId, "Kenji", "k•••••@gmail.com");
        }
        String name = Character.toUpperCase(customerId.charAt(0)) + customerId.substring(1);
        return new Me(customerId, name, customerId.charAt(0) + "•••••@example.com");
    }

    // ---------- M-05 to M-12: filled in by Task 3 ----------

    @Override
    public synchronized List<TierAvailability> availability(String eventId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized HoldView hold(String customerId, String token, String tierId, int quantity) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized HoldView getHold(String customerId, String reservationId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized HoldView release(String customerId, String reservationId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized PaymentStart startPayment(String customerId, String reservationId, String providerKey) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized PaymentStatus answerPayment(String customerId, String paymentId, boolean approve) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized PaymentStatus paymentStatus(String customerId, String paymentId) {
        throw new UnsupportedOperationException("Task 3");
    }

    @Override
    public synchronized List<OrderView> orders(String customerId) {
        throw new UnsupportedOperationException("Task 3");
    }

    // ---------- dev controls ----------

    @Override
    public synchronized void scenario(Scenario scenario) {
        Event e = events.get("the-1975");
        switch (scenario) {
            case EVENT_CANCELLED -> e.cancelled = true;
            case SALE_CLOSED -> e.closed = true;
            case SOLD_OUT_ALL -> e.tiers.forEach(t -> t.sold = t.total - t.held);
            default -> scenarios.add(scenario);
        }
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
        clock.set(START);
        seed();
    }
}
