package eventflow.web.real;

import eventflow.core.inventory.HoldResult;
import eventflow.core.inventory.InventoryService;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.CheckoutResult;
import eventflow.core.order.Reservation;
import eventflow.core.order.ReservationState;
import eventflow.core.queue.JoinResult.Outcome;
import eventflow.core.queue.QueueToken;
import eventflow.core.queue.QueueTokenState;
import eventflow.web.BookingGateway;
import eventflow.web.GatewayException;
import eventflow.web.dto.Views.EventDetail;
import eventflow.web.dto.Views.EventSummary;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.dto.Views.JoinResult;
import eventflow.web.dto.Views.Me;
import eventflow.web.dto.Views.OrderView;
import eventflow.web.dto.Views.PaymentMethod;
import eventflow.web.dto.Views.PaymentStart;
import eventflow.web.dto.Views.PaymentStatus;
import eventflow.web.dto.Views.QueueStatus;
import eventflow.web.dto.Views.TicketView;
import eventflow.web.dto.Views.TierAvailability;
import eventflow.web.dto.Views.TierPrice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The customer pages on the real booking engine (core/). Where core has no answer yet,
 * RealEngine's own records fill the gap, or the call is refused with NOT_IN_CORE.
 */
public final class CoreBookingGateway implements BookingGateway {

    record PaymentMethodRow(String key, String tile, String label, String line) {
    }

    static final List<PaymentMethodRow> PROVIDERS = List.of(
            new PaymentMethodRow("promptpay", "PP", "PromptPay", "Scan with your banking app"),
            new PaymentMethodRow("card", "CARD", "Credit or debit card", "Visa, Mastercard"));

    private final RealEngine engine;

    public CoreBookingGateway(RealEngine engine) {
        this.engine = engine;
    }

    @Override
    public long now() {
        return engine.clock.now();
    }

    @Override
    public int clockRate() {
        return engine.clock.speed();
    }

    @Override
    public List<EventSummary> listEvents(String customerId) {                                // M-01
        synchronized (engine) {
            engine.tick();
            return engine.events.values().stream()
                    .filter(e -> !e.draft())
                    .sorted(Comparator.comparingLong(e -> e.saleOpensAt))
                    .map(e -> new EventSummary(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                            engine.state(e), fromPrice(e), e.artwork, engine.flow.inventory().soldOut(e.coreId)))
                    .toList();
        }
    }

    @Override
    public EventDetail getEvent(String customerId, String eventId) {                         // M-02
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = engine.customerEvent(eventId);
            QueueToken mine = activeToken(customerId, eventId);
            Integer position = mine != null && mine.state() == QueueTokenState.WAITING
                    ? position(mine.tokenId()) : null;
            return new EventDetail(e.id, e.name, e.shortName, e.venue, e.dateTime, e.saleOpensAt,
                    engine.state(e), e.artwork, engine.flow.inventory().soldOut(e.coreId),
                    engine.coreTiers(e).stream().map(t -> new TierPrice(t.tierId(), t.name(), t.price().baht())).toList(),
                    mine == null ? null : mine.tokenId(), position);
        }
    }

    @Override
    public JoinResult joinQueue(String customerId, String eventId) {                         // M-03
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = engine.customerEvent(eventId);
            if (!engine.state(e).equals("ON_SALE")) {
                throw notOnSale();
            }
            var r = engine.flow.queue().join(customerId, e.coreId);
            if (r.outcome() == Outcome.SALE_NOT_OPEN) {
                throw notOnSale();
            }
            if (r.outcome() == Outcome.SOLD_OUT) {
                throw GatewayException.refused("SOLD_OUT", "Every ticket has been sold", 0);
            }
            String token = r.token().tokenId();
            engine.myTokens.put(customerId + "@" + eventId, token);
            return new JoinResult(token, eventId, position(token), r.outcome() == Outcome.ALREADY_IN_QUEUE);
        }
    }

    @Override
    public QueueStatus queueStatus(String customerId, String tokenId) {                      // M-04
        synchronized (engine) {
            engine.tick();
            QueueToken t = ownedToken(customerId, tokenId);
            String reservationId = engine.flow.store().all().stream()
                    .filter(r -> r.tokenId().equals(tokenId)).map(Reservation::reservationId)
                    .findFirst().orElse(null);
            return new QueueStatus(tokenId, engine.byCore(t.eventId()).id, t.state().name(), position(tokenId),
                    engine.admitted.getOrDefault(t.eventId(), 0), engine.flow.queue().admissionRate(t.eventId()),
                    reservationId);
        }
    }

    @Override
    public List<TierAvailability> availability(String eventId) {                             // M-05
        synchronized (engine) {
            engine.tick();
            return engine.coreTiers(engine.customerEvent(eventId)).stream()
                    .map(t -> new TierAvailability(t.tierId(), t.name(), t.price().baht(), t.remaining(),
                            t.totalQuantity()))
                    .toList();
        }
    }

    @Override
    public Me me(String customerId) {                                                        // M-13, stand-in
        if (customerId.equals("kenji")) {
            return new Me(customerId, "Kenji", "k•••••@gmail.com");
        }
        String name = Character.toUpperCase(customerId.charAt(0)) + customerId.substring(1);
        return new Me(customerId, name, customerId.charAt(0) + "•••••@example.com");
    }

    @Override
    public HoldView hold(String customerId, String tokenId, String tierId, int quantity) {     // M-06
        synchronized (engine) {
            engine.tick();
            QueueToken t = ownedToken(customerId, tokenId);
            // Core counts seats before it looks at the token; check the turn first, as the fake does.
            if (t.state() != QueueTokenState.ADMITTED) {
                throw GatewayException.badRequest("NOT_ADMITTED", "This place in line cannot hold seats now");
            }
            RealEngine.EventInfo e = engine.byCore(t.eventId());
            if (!engine.state(e).equals("ON_SALE")) {
                throw notOnSale();
            }
            TicketTier tier = engine.coreTiers(e).stream().filter(x -> x.tierId().equals(tierId)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Tier"));
            if (quantity < 1 || quantity > InventoryService.ORDER_LIMIT) {
                throw GatewayException.refused("OVER_LIMIT", "At most 4 tickets per person", tier.remaining());
            }
            int before = tier.remaining();
            HoldResult r = engine.flow.inventory().hold(tokenId, tierId, quantity);
            switch (r.outcome()) {
                case HELD -> { }
                case NOT_ENOUGH_SEATS -> throw engine.flow.inventory().soldOut(e.coreId)
                        ? GatewayException.refused("SOLD_OUT", "Every ticket has been sold", 0)
                        : GatewayException.refused("NOT_ENOUGH", "Not enough seats left in this tier", tier.remaining());
                case OVER_ORDER_LIMIT ->
                        throw GatewayException.refused("OVER_LIMIT", "At most 4 tickets per person", tier.remaining());
                default -> throw GatewayException.badRequest("NOT_ADMITTED", "This place in line cannot hold seats now");
            }
            RealEngine.HoldInfo h = new RealEngine.HoldInfo(r.reservation().reservationId(), customerId, e.id,
                    before, tier.remaining());
            engine.holds.put(h.reservationId, h);
            return view(h);
        }
    }

    @Override
    public HoldView getHold(String customerId, String reservationId) {                       // M-07
        synchronized (engine) {
            engine.tick();
            return view(ownedHold(customerId, reservationId));
        }
    }

    RealEngine.HoldInfo ownedHold(String customerId, String reservationId) {
        RealEngine.HoldInfo h = engine.holds.get(reservationId);
        if (h == null || !h.customerId.equals(customerId)) {
            throw GatewayException.notFound("Hold");
        }
        return h;
    }

    HoldView view(RealEngine.HoldInfo h) {
        Reservation r = engine.flow.store().find(h.reservationId).orElseThrow();
        TicketTier tier = engine.flow.inventory().tier(r.tierId());
        List<PaymentMethod> methods = PROVIDERS.stream()
                .map(p -> new PaymentMethod(p.key(), p.tile(), p.label(), p.line(),
                        engine.flow.registry().paymentKeys().contains(p.key())))
                .toList();
        // Promotions are Slice 3 (PricingRule), so core charges the list price.
        return new HoldView(r.reservationId(), r.tokenId(), r.state().name(), engine.ref(engine.event(h.eventId)),
                tier.tierId(), tier.name(), r.quantity(), r.unitPrice().baht(), List.of(), r.totalAmount().baht(),
                r.expiresAt().toEpochMilli(), h.before, h.after, methods, h.paymentId);
    }

    String providerLabel(String key) {
        return PROVIDERS.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow().label();
    }

    @Override
    public HoldView release(String customerId, String reservationId) {                       // M-08
        synchronized (engine) {
            engine.tick();
            ownedHold(customerId, reservationId);
            throw RealEngine.notInCore("Giving seats back early");
        }
    }

    @Override
    public PaymentStart startPayment(String customerId, String reservationId, String providerKey) { // M-09
        synchronized (engine) {
            engine.tick();
            RealEngine.HoldInfo h = ownedHold(customerId, reservationId);
            Reservation r = engine.flow.store().find(reservationId).orElseThrow();
            if (r.state() != ReservationState.HELD) {
                throw GatewayException.badRequest("HOLD_NOT_ACTIVE", "These seats are no longer held");
            }
            PaymentMethodRow method = PROVIDERS.stream().filter(p -> p.key().equals(providerKey)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Payment method"));
            if (!engine.flow.registry().paymentKeys().contains(method.key())) {
                throw GatewayException.badRequest("PROVIDER_UNAVAILABLE", "This way to pay is not available right now");
            }
            if (h.paymentId != null) {
                RealEngine.PaymentInfo last = engine.payments.get(h.paymentId);
                if (last.state.equals("PENDING") && !last.answered && last.providerKey.equals(method.key())) {
                    return start(last);
                }
                if (last.state.equals("PENDING") && !last.answered) {
                    last.state = "VOIDED"; // a newer way to pay replaced it; it can no longer be answered
                }
            }
            RealEngine.PaymentInfo p = new RealEngine.PaymentInfo("P-" + (++engine.paymentSeq), reservationId,
                    customerId, method.key(), r.totalAmount().baht());
            engine.payments.put(p.id, p);
            h.paymentId = p.id;
            return start(p);
        }
    }

    @Override
    public PaymentStatus answerPayment(String customerId, String paymentId, boolean approve) {  // M-10
        synchronized (engine) {
            engine.tick();
            RealEngine.PaymentInfo p = ownedPayment(customerId, paymentId);
            RealEngine.HoldInfo h = engine.holds.get(p.reservationId);
            if (!p.id.equals(h.paymentId)) {
                throw GatewayException.badRequest("PAYMENT_REPLACED", "This way to pay was replaced by a newer one");
            }
            if (p.state.equals("PENDING") && !p.answered) {
                p.answered = true;
                if (!approve) {
                    p.state = "DECLINED";
                } else {
                    CheckoutResult result = engine.flow.checkout().checkout(p.reservationId, p.providerKey);
                    switch (result) {
                        case CONFIRMED -> {
                            p.state = "CAPTURED";
                            p.orderId = order(h, p).id;
                        }
                        case DECLINED -> p.state = "DECLINED";
                        case HOLD_EXPIRED -> p.state = "VOIDED";
                    }
                }
            }
            return status(p);
        }
    }

    @Override
    public PaymentStatus paymentStatus(String customerId, String paymentId) {                 // M-11
        synchronized (engine) {
            engine.tick();
            return status(ownedPayment(customerId, paymentId));
        }
    }

    @Override
    public List<OrderView> orders(String customerId) {                                       // M-12, stand-in
        synchronized (engine) {
            engine.tick();
            long now = engine.clock.now();
            return engine.orders.values().stream()
                    .filter(o -> o.customerId.equals(customerId))
                    .map(o -> orderView(o, now))
                    .sorted(Comparator.comparing(OrderView::past)
                            .thenComparingLong(v -> v.past() ? -v.event().eventDateTime() : v.event().eventDateTime()))
                    .toList();
        }
    }

    // ---------- payment and order helpers ----------

    private RealEngine.PaymentInfo ownedPayment(String customerId, String paymentId) {
        RealEngine.PaymentInfo p = engine.payments.get(paymentId);
        if (p == null || !p.customerId.equals(customerId)) {
            throw GatewayException.notFound("Payment");
        }
        return p;
    }

    private PaymentStart start(RealEngine.PaymentInfo p) {
        return new PaymentStart(p.id, p.reservationId, p.providerKey, providerLabel(p.providerKey), p.amount,
                "PROMPTPAY|" + p.id + "|" + p.amount);
    }

    private static PaymentStatus status(RealEngine.PaymentInfo p) {
        return new PaymentStatus(p.id, p.reservationId, p.providerKey, p.state, p.answered, p.orderId);
    }

    /** Core confirmed the order (OrderConfirmed). Tickets are Slice 3, so the gateway numbers them for now. */
    private RealEngine.OrderInfo order(RealEngine.HoldInfo h, RealEngine.PaymentInfo p) {
        Reservation r = engine.flow.store().find(h.reservationId).orElseThrow();
        TicketTier tier = engine.flow.inventory().tier(r.tierId());
        RealEngine.OrderInfo o = new RealEngine.OrderInfo("O-" + (++engine.orderSeq), h.customerId, h.eventId,
                tier.tierId(), tier.name(), r.quantity(), p.amount, providerLabel(p.providerKey));
        for (int n = 1; n <= r.quantity(); n++) {
            o.ticketIds.add(String.format("TKT-%s-%02d", o.id, n));
        }
        engine.orders.put(o.id, o);
        return o;
    }

    private OrderView orderView(RealEngine.OrderInfo o, long now) {
        RealEngine.EventInfo e = engine.event(o.eventId);
        List<TicketView> tickets = new ArrayList<>();
        for (int i = 0; i < o.ticketIds.size(); i++) {
            String id = o.ticketIds.get(i);
            // Not signed: signing tickets is Slice 3 (NFR-06).
            String payload = "EVENTFLOW|" + id + "|" + o.eventId + "|" + o.tierId + "|" + o.customerId + "|UNSIGNED";
            tickets.add(new TicketView(id, i + 1, o.ticketIds.size(), payload, "VALID"));
        }
        return new OrderView(o.id, engine.ref(e), engine.state(e), o.tierId, o.tierName, o.quantity, o.paid,
                o.providerLabel, now > e.dateTime, tickets);
    }

    // ---------- helpers ----------

    private long fromPrice(RealEngine.EventInfo e) {
        return engine.coreTiers(e).stream().mapToLong(t -> t.price().baht()).min().orElse(0);
    }

    private QueueToken activeToken(String customerId, String eventId) {
        String id = engine.myTokens.get(customerId + "@" + eventId);
        if (id == null) {
            return null;
        }
        QueueToken t = engine.flow.queue().token(id);
        return t.state() == QueueTokenState.WAITING || t.state() == QueueTokenState.ADMITTED ? t : null;
    }

    private QueueToken ownedToken(String customerId, String tokenId) {
        try {
            QueueToken t = engine.flow.queue().token(tokenId);
            if (t.customerId().equals(customerId)) {
                return t;
            }
        } catch (IllegalArgumentException unknown) {
            // falls through to not found
        }
        throw GatewayException.notFound("Place in line");
    }

    private int position(String tokenId) {
        return (int) engine.flow.queue().status(tokenId).position();
    }

    private static GatewayException notOnSale() {
        return GatewayException.badRequest("NOT_ON_SALE", "This sale is not open");
    }
}
