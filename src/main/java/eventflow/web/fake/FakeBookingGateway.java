package eventflow.web.fake;

import eventflow.web.BookingGateway;
import eventflow.web.GatewayException;
import eventflow.web.dto.Views.EventDetail;
import eventflow.web.dto.Views.EventRef;
import eventflow.web.dto.Views.EventSummary;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.dto.Views.JoinResult;
import eventflow.web.dto.Views.Me;
import eventflow.web.dto.Views.OrderView;
import eventflow.web.dto.Views.PaymentMethod;
import eventflow.web.dto.Views.PaymentStart;
import eventflow.web.dto.Views.PaymentStatus;
import eventflow.web.dto.Views.Promotion;
import eventflow.web.dto.Views.QueueStatus;
import eventflow.web.dto.Views.TicketView;
import eventflow.web.dto.Views.TierAvailability;
import eventflow.web.dto.Views.TierPrice;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    final Map<String, Reservation> reservations = new LinkedHashMap<>();
    final Map<String, Payment> payments = new LinkedHashMap<>();
    final Map<String, Order> orders = new LinkedHashMap<>();
    int reservationSeq;
    int paymentSeq;
    int orderSeq;

    record Provider(String key, String tile, String label, String line) {
    }

    static final List<Provider> PROVIDERS = List.of(
            new Provider("promptpay", "PP", "PromptPay", "Scan with your banking app"),
            new Provider("card", "CARD", "Credit or debit card", "Visa, Mastercard"));

    public FakeBookingGateway(FakeClock clock) {
        this.clock = clock;
        seed();
    }

    static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, BANGKOK).toInstant().toEpochMilli();
    }

    // ---------- data ----------

    static final class Event {
        final String id;
        final String artwork;
        String name, shortName, venue;
        long dateTime, saleOpensAt;
        final List<Tier> tiers = new ArrayList<>();
        boolean cancelled, closed;
        String owner = "chai";
        boolean draft, closing;
        Long closedAt;
        int admitPerSecond = ADMIT_PER_SECOND;

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
            return new EventRef(id, name, shortName, venue, dateTime, artwork);
        }
    }

    static final class Tier {
        final String id;
        String name;
        long price;
        int total;
        int held, sold;
        int soldByOrders;

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

    static final class Reservation {
        final String id, tokenId, customerId, eventId, tierId;
        final int quantity, before, after;
        final long unitPrice, heldAt, expiresAt;
        String state = "HELD";
        String paymentId;

        Reservation(String id, String tokenId, String customerId, String eventId, String tierId, int quantity,
                    long unitPrice, long heldAt, long expiresAt, int before, int after) {
            this.id = id;
            this.tokenId = tokenId;
            this.customerId = customerId;
            this.eventId = eventId;
            this.tierId = tierId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.heldAt = heldAt;
            this.expiresAt = expiresAt;
            this.before = before;
            this.after = after;
        }
    }

    static final class Payment {
        final String id, reservationId, customerId, providerKey;
        final long amount, requestedAt;
        String state = "PENDING";
        boolean answered, approve;
        long settleAt;
        String orderId;

        Payment(String id, String reservationId, String customerId, String providerKey, long amount, long requestedAt) {
            this.id = id;
            this.reservationId = reservationId;
            this.customerId = customerId;
            this.providerKey = providerKey;
            this.amount = amount;
            this.requestedAt = requestedAt;
        }
    }

    static final class Order {
        final String id, customerId, tierId, tierName, providerLabel;
        final EventRef event;
        final int quantity;
        final long paid;
        final List<String> ticketIds = new ArrayList<>();

        Order(String id, String customerId, EventRef event, String tierId, String tierName, int quantity,
              long paid, String providerLabel) {
            this.id = id;
            this.customerId = customerId;
            this.event = event;
            this.tierId = tierId;
            this.tierName = tierName;
            this.quantity = quantity;
            this.paid = paid;
            this.providerLabel = providerLabel;
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

    void seedOrders() {
        reservations.clear();
        payments.clear();
        orders.clear();
        reservationSeq = 1041;
        paymentSeq = 5000;
        orderSeq = 9000;
        EventRef past = new EventRef("bruno-mars-aug", "Bruno Mars", "BRUNO MARS", "Thunder Dome",
                at(2026, 8, 2, 20, 0), "assets/bruno-mars.png");
        Order o = new Order("O-0001", "kenji", past, "zone-a", "Zone A", 1, 3800, "PromptPay");
        o.ticketIds.add("TKT-3B17-01");
        orders.put(o.id, o);
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

    /** An event a customer may know about: a draft does not exist yet. */
    Event customerEvent(String eventId) {
        Event e = event(eventId);
        if (e.draft) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    /** The organizer's state. CLOSING turns into CLOSED (and stamps closedAt) once no seat is held. */
    String orgState(Event e) {
        if (e.draft) {
            return "DRAFT";
        }
        if (e.cancelled) {
            return "CANCELLED";
        }
        if (e.closed) {
            return "CLOSED";
        }
        if (e.closing) {
            if (e.tiers.stream().anyMatch(t -> t.held > 0)) {
                return "CLOSING";
            }
            e.closed = true;
            e.closedAt = clock.now();
            return "CLOSED";
        }
        return clock.now() < e.saleOpensAt ? "SCHEDULED" : "ON_SALE";
    }

    /** What a customer sees: a closing sale already reads as closed (SCR-05e). */
    String state(Event e) {
        String s = orgState(e);
        return s.equals("CLOSING") ? "CLOSED" : s;
    }

    Token ownedToken(String customerId, String tokenId) {
        Token t = tokens.get(tokenId);
        if (t == null || !t.customerId.equals(customerId)) {
            throw GatewayException.notFound("Place in line");
        }
        refresh(t);
        return t;
    }

    long admitAfterMs(Event e) {
        return (long) Math.ceil(START_AHEAD * 1000.0 / e.admitPerSecond);
    }

    void refresh(Token t) {
        long now = clock.now();
        Event e = events.get(t.eventId);
        long after = admitAfterMs(e);
        // FR-29: once the sale is closing or closed, nobody new is let in.
        if (t.state.equals("WAITING") && now >= t.issuedAt + after && orgState(e).equals("ON_SALE")) {
            t.state = "ADMITTED";
            t.admittedAt = t.issuedAt + after;
        }
        if (t.state.equals("ADMITTED") && now - t.admittedAt > TURN_LIMIT_MS) {
            t.state = "EXPIRED";
        }
    }

    int admittedSoFar(Token t) {
        long elapsed = Math.max(0, clock.now() - t.issuedAt);
        return (int) Math.min(START_AHEAD, elapsed * events.get(t.eventId).admitPerSecond / 1000);
    }

    int position(Token t) {
        return t.state.equals("WAITING") ? Math.max(1, START_AHEAD - admittedSoFar(t)) : 0;
    }

    // ---------- organizer reads ----------

    static final int CROWD_1975 = 35_768;

    /** People waiting now. The 1975 also has the crowd from the story: 31,208 at 10:06:20 at 12 per second. */
    int waiting(Event e) {
        if (!orgState(e).equals("ON_SALE")) {
            return 0;
        }
        long real = tokens.values().stream().filter(t -> t.eventId.equals(e.id))
                .peek(this::refresh).filter(t -> t.state.equals("WAITING")).count();
        long crowd = e.id.equals("the-1975")
                ? Math.max(0, CROWD_1975 - (clock.now() - e.saleOpensAt) * e.admitPerSecond / 1000)
                : 0;
        return (int) (crowd + real);
    }

    /** Confirmed money for this event: seats sold before the demo at list price, plus what each order paid. */
    long revenue(Event e) {
        long seeded = e.tiers.stream().mapToLong(t -> (long) (t.sold - t.soldByOrders) * t.price).sum();
        long paid = orders.values().stream().filter(o -> o.event.eventId().equals(e.id)).mapToLong(o -> o.paid).sum();
        return seeded + paid;
    }

    /** Settles payments that are due and expires holds whose 10 minutes are up. */
    void sweep() {
        for (Reservation r : reservations.values()) {
            refresh(r);
        }
    }

    void refresh(Reservation r) {
        if (r.paymentId != null) {
            settle(payments.get(r.paymentId));
        }
        if (r.state.equals("HELD") && clock.now() >= r.expiresAt) {
            r.state = "EXPIRED";
            tierOf(r).held -= r.quantity;
        }
    }

    void settle(Payment p) {
        if (!p.state.equals("PENDING") || !p.answered || clock.now() < p.settleAt) {
            return;
        }
        Reservation r = reservations.get(p.reservationId);
        if (!p.approve) {
            p.state = "DECLINED";
            return;
        }
        if (p.settleAt >= r.expiresAt || !r.state.equals("HELD")) {
            p.state = "VOIDED";
            if (r.state.equals("HELD")) {
                r.state = "EXPIRED";
                tierOf(r).held -= r.quantity;
            }
            return;
        }
        Tier t = tierOf(r);
        p.state = "CAPTURED";
        r.state = "CONFIRMED";
        t.held -= r.quantity;
        t.sold += r.quantity;
        t.soldByOrders += r.quantity;
        Order o = new Order("O-" + (++orderSeq), r.customerId, event(r.eventId).ref(), t.id, t.name,
                r.quantity, p.amount, providerLabel(p.providerKey));
        for (int n = 1; n <= r.quantity; n++) {
            o.ticketIds.add(String.format("TKT-%04X-%02d", 0x9F42 + orderSeq - 9001, n));
        }
        orders.put(o.id, o);
        p.orderId = o.id;
    }

    Tier tierOf(Reservation r) {
        return event(r.eventId).tier(r.tierId);
    }

    Reservation ownedHold(String customerId, String reservationId) {
        Reservation r = reservations.get(reservationId);
        if (r == null || !r.customerId.equals(customerId)) {
            throw GatewayException.notFound("Hold");
        }
        return r;
    }

    Payment ownedPayment(String customerId, String paymentId) {
        Payment p = payments.get(paymentId);
        if (p == null || !p.customerId.equals(customerId)) {
            throw GatewayException.notFound("Payment");
        }
        return p;
    }

    boolean available(String providerKey) {
        if (scenarios.contains(Scenario.ALL_PROVIDERS_DOWN)) {
            return false;
        }
        return !(providerKey.equals("card") && scenarios.contains(Scenario.PROVIDER_DOWN));
    }

    String providerLabel(String key) {
        return PROVIDERS.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().label();
    }

    List<Promotion> promotions(Reservation r) {
        if (r.heldAt < event(r.eventId).saleOpensAt + 30 * 60_000L) {
            return List.of(new Promotion("Early Bird −10%", Math.round(r.unitPrice * r.quantity * 0.10)));
        }
        return List.of();
    }

    long total(Reservation r) {
        return r.unitPrice * r.quantity - promotions(r).stream().mapToLong(Promotion::amount).sum();
    }

    HoldView view(Reservation r) {
        Tier t = tierOf(r);
        List<PaymentMethod> methods = PROVIDERS.stream()
                .map(p -> new PaymentMethod(p.key(), p.tile(), p.label(), p.line(), available(p.key())))
                .toList();
        return new HoldView(r.id, r.tokenId, r.state, event(r.eventId).ref(), t.id, t.name, r.quantity,
                r.unitPrice, promotions(r), total(r), r.expiresAt, r.before, r.after, methods, r.paymentId);
    }

    PaymentStart start(Payment p) {
        String display = p.providerKey.equals("promptpay")
                ? "PROMPTPAY|" + p.id + "|" + p.amount
                : "Merchant: Event Ticket & Queue";
        return new PaymentStart(p.id, p.reservationId, p.providerKey, providerLabel(p.providerKey), p.amount, display);
    }

    PaymentStatus status(Payment p) {
        return new PaymentStatus(p.id, p.reservationId, p.providerKey, p.state, p.answered, p.orderId);
    }

    OrderView orderView(Order o, long now) {
        Event live = events.get(o.event.eventId());
        String eventState = live == null ? "CLOSED" : state(live);
        String ticketState = eventState.equals("CANCELLED") ? "CANCELLED" : "VALID";
        List<TicketView> tickets = new ArrayList<>();
        for (int i = 0; i < o.ticketIds.size(); i++) {
            String id = o.ticketIds.get(i);
            tickets.add(new TicketView(id, i + 1, o.ticketIds.size(), payload(id, o), ticketState));
        }
        return new OrderView(o.id, o.event, eventState, o.tierId, o.tierName, o.quantity, o.paid,
                o.providerLabel, now > o.event.eventDateTime(), tickets);
    }

    static String payload(String ticketId, Order o) {
        String body = "EVENTFLOW|" + ticketId + "|" + o.event.eventId() + "|" + o.tierId + "|" + o.customerId;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("eventflow-demo-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return body + "|" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
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
                .filter(e -> !e.cancelled && !e.draft)
                .sorted(Comparator.comparingLong(e -> e.saleOpensAt))
                .map(e -> new EventSummary(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                        state(e), e.tiers.stream().mapToLong(t -> t.price).min().orElse(0), e.artwork, e.soldOut()))
                .toList();
    }

    @Override
    public synchronized EventDetail getEvent(String customerId, String eventId) {
        sweep();
        Event e = customerEvent(eventId);
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
        Event e = customerEvent(eventId);
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
        String reservationId = reservations.values().stream()
                .filter(r -> r.tokenId.equals(t.id)).map(r -> r.id).findFirst().orElse(null);
        return new QueueStatus(t.id, t.eventId, t.state, position(t), START_MOVED + admittedSoFar(t),
                event(t.eventId).admitPerSecond, reservationId);
    }

    @Override
    public synchronized Me me(String customerId) {
        if (customerId.equals("kenji")) {
            return new Me(customerId, "Kenji", "k•••••@gmail.com");
        }
        String name = Character.toUpperCase(customerId.charAt(0)) + customerId.substring(1);
        return new Me(customerId, name, customerId.charAt(0) + "•••••@example.com");
    }

    // ---------- M-05 to M-12 ----------

    @Override
    public synchronized List<TierAvailability> availability(String eventId) {
        sweep();
        return customerEvent(eventId).tiers.stream()
                .map(t -> new TierAvailability(t.id, t.name, t.price, t.remaining(), t.total))
                .toList();
    }

    @Override
    public synchronized HoldView hold(String customerId, String tokenId, String tierId, int quantity) {
        sweep();
        Token t = ownedToken(customerId, tokenId);
        if (scenarios.remove(Scenario.TURN_PASSED) && t.state.equals("ADMITTED")) {
            t.state = "EXPIRED";
        }
        if (!t.state.equals("ADMITTED")) {
            throw GatewayException.badRequest("NOT_ADMITTED", "This place in line cannot hold seats now");
        }
        Event e = event(t.eventId);
        if (!state(e).equals("ON_SALE")) {
            throw GatewayException.badRequest("NOT_ON_SALE", "This sale is not open");
        }
        Tier tier = e.tier(tierId);
        if (quantity < 1 || quantity > ORDER_LIMIT) {
            throw GatewayException.refused("OVER_LIMIT", "At most 4 tickets per person", tier.remaining());
        }
        if (e.soldOut()) {
            throw GatewayException.refused("SOLD_OUT", "Every ticket has been sold", 0);
        }
        if (scenarios.remove(Scenario.RACE_LOST)) {
            tier.sold += Math.min(tier.remaining(), 2);
            throw GatewayException.refused("RACE_LOST", "Those seats just went to someone else", tier.remaining());
        }
        if (tier.remaining() < quantity) {
            throw GatewayException.refused("NOT_ENOUGH", "Not enough seats left in this tier", tier.remaining());
        }
        int before = tier.remaining();
        tier.held += quantity;
        long now = clock.now();
        Reservation r = new Reservation("R-" + (++reservationSeq), t.id, customerId, e.id, tier.id, quantity,
                tier.price, now, now + HOLD_MS, before, tier.remaining());
        reservations.put(r.id, r);
        t.state = "SPENT";
        return view(r);
    }

    @Override
    public synchronized HoldView getHold(String customerId, String reservationId) {
        sweep();
        return view(ownedHold(customerId, reservationId));
    }

    @Override
    public synchronized HoldView release(String customerId, String reservationId) {
        sweep();
        Reservation r = ownedHold(customerId, reservationId);
        if (!r.state.equals("HELD")) {
            throw GatewayException.badRequest("HOLD_NOT_ACTIVE", "These seats are no longer held");
        }
        r.state = "RELEASED";
        tierOf(r).held -= r.quantity;
        return view(r);
    }

    @Override
    public synchronized PaymentStart startPayment(String customerId, String reservationId, String providerKey) {
        sweep();
        Reservation r = ownedHold(customerId, reservationId);
        if (!r.state.equals("HELD")) {
            throw GatewayException.badRequest("HOLD_NOT_ACTIVE", "These seats are no longer held");
        }
        Provider provider = PROVIDERS.stream().filter(p -> p.key().equals(providerKey)).findFirst()
                .orElseThrow(() -> GatewayException.notFound("Payment method"));
        if (!available(provider.key())) {
            throw GatewayException.badRequest("PROVIDER_UNAVAILABLE", "This way to pay is not available right now");
        }
        if (r.paymentId != null) {
            Payment last = payments.get(r.paymentId);
            if (last.state.equals("PENDING") && last.answered) {
                throw GatewayException.badRequest("PAYMENT_IN_PROGRESS", "A payment for these seats is already being confirmed");
            }
            if (last.state.equals("PENDING") && !last.answered && last.providerKey.equals(provider.key())) {
                return start(last);
            }
        }
        if (r.paymentId != null) {
            Payment replaced = payments.get(r.paymentId);
            if (replaced.state.equals("PENDING") && !replaced.answered) {
                replaced.state = "VOIDED"; // a newer way to pay replaced it; it can no longer be answered
            }
        }
        Payment p = new Payment("P-" + (++paymentSeq), r.id, customerId, provider.key(), total(r), clock.now());
        payments.put(p.id, p);
        r.paymentId = p.id;
        return start(p);
    }

    @Override
    public synchronized PaymentStatus answerPayment(String customerId, String paymentId, boolean approve) {
        sweep();
        Payment p = ownedPayment(customerId, paymentId);
        if (!p.id.equals(reservations.get(p.reservationId).paymentId)) {
            throw GatewayException.badRequest("PAYMENT_REPLACED", "This way to pay was replaced by a newer one");
        }
        if (p.state.equals("PENDING") && !p.answered) {
            p.answered = true;
            p.approve = approve;
            p.settleAt = clock.now() + (scenarios.contains(Scenario.BANK_SLOW) ? 12_000 : 2_000);
        }
        return status(p);
    }

    @Override
    public synchronized PaymentStatus paymentStatus(String customerId, String paymentId) {
        sweep();
        return status(ownedPayment(customerId, paymentId));
    }

    @Override
    public synchronized List<OrderView> orders(String customerId) {
        sweep();
        long now = clock.now();
        return orders.values().stream()
                .filter(o -> o.customerId.equals(customerId))
                .map(o -> orderView(o, now))
                .sorted(Comparator.comparing(OrderView::past)
                        .thenComparingLong(v -> v.past() ? -v.event().eventDateTime() : v.event().eventDateTime()))
                .toList();
    }

    // ---------- dev controls ----------

    @Override
    public synchronized void scenario(Scenario scenario) {
        Event e = events.get("the-1975");
        switch (scenario) {
            case EVENT_CANCELLED -> e.cancelled = true;
            case SALE_CLOSED -> {
                e.closed = true;
                e.closedAt = clock.now();
            }
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
