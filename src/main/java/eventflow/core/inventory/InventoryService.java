package eventflow.core.inventory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import eventflow.contract.Money;
import eventflow.contract.PricingRequest;
import eventflow.core.event.EventBus;
import eventflow.core.event.HoldExpired;
import eventflow.core.event.HoldReleased;
import eventflow.core.event.OrderConfirmed;
import eventflow.core.event.TicketHeld;
import eventflow.core.order.PricingService;
import eventflow.core.order.Reservation;
import eventflow.core.order.ReservationStore;
import eventflow.core.queue.ClaimResult;
import eventflow.core.queue.QueueService;

// The only class allowed to change held and sold counts.
public final class InventoryService {

    public static final int ORDER_LIMIT = 4;

    private final Map<String, TicketTier> tiers = new ConcurrentHashMap<>();
    private final Set<String> settled = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextReservation = new AtomicLong(1001);
    private final AtomicLong nextTier = new AtomicLong(1);
    private final ReservationStore store;
    private final QueueService queue;
    private final PricingService pricing;
    private final EventBus bus;
    private final Clock clock;

    public InventoryService(ReservationStore store, QueueService queue, PricingService pricing,
                            EventBus bus, Clock clock) {
        this.store = store;
        this.queue = queue;
        this.pricing = pricing;
        this.bus = bus;
        this.clock = clock;
        bus.subscribe(HoldExpired.class, this::onHoldExpired);
        bus.subscribe(OrderConfirmed.class, this::onOrderConfirmed);
        bus.subscribe(HoldReleased.class, this::onHoldReleased);
    }

    public TicketTier createTier(String eventId, String name, Money price, int totalQuantity) {
        TicketTier tier = new TicketTier("T-" + nextTier.getAndIncrement(),
                                         eventId, name, price, totalQuantity);
        tiers.put(tier.tierId(), tier);
        return tier;
    }

    // Organizer changes. Rules about when they are allowed live in EventService.
    public void resizeTier(String tierId, int newTotal) { tier(tierId).resize(newTotal); }
    public void renameTier(String tierId, String newName) { tier(tierId).rename(newName); }
    public void repriceTier(String tierId, Money newPrice) { tier(tierId).reprice(newPrice); }

    public TicketTier tier(String tierId) {
        TicketTier tier = tiers.get(tierId);
        if (tier == null) {
            throw new IllegalArgumentException("No tier " + tierId);
        }
        return tier;
    }

    public List<TicketTier> tiersOf(String eventId) {
        return tiers.values().stream()
                .filter(t -> t.eventId().equals(eventId))
                .sorted((a, b) -> a.tierId().compareTo(b.tierId()))
                .toList();
    }

    public boolean soldOut(String eventId) {
        List<TicketTier> eventTiers = tiersOf(eventId);
        return !eventTiers.isEmpty() && eventTiers.stream().allMatch(t -> t.remaining() == 0);
    }

    public HoldResult hold(String tokenId, String tierId, int quantity) {
        if (quantity < 1 || quantity > ORDER_LIMIT) {
            return HoldResult.refused(HoldResult.Outcome.OVER_ORDER_LIMIT);
        }
        TicketTier tier = tier(tierId);
        Reservation reservation;
        // Check seats, use the token and count the hold under one lock,
        // so two buyers can never both get the last seat.
        synchronized (tier) {
            if (tier.remaining() < quantity) {
                return HoldResult.refused(HoldResult.Outcome.NOT_ENOUGH_SEATS);
            }
            ClaimResult claim = queue.claimForHold(tokenId, tier.eventId());
            if (claim == ClaimResult.SALE_CLOSED) {
                return HoldResult.refused(HoldResult.Outcome.SALE_CLOSED);
            }
            if (claim == ClaimResult.NOT_ADMITTED) {
                return HoldResult.refused(HoldResult.Outcome.NOT_ADMITTED);
            }
            if (claim == ClaimResult.ALREADY_USED) {
                return HoldResult.refused(HoldResult.Outcome.TOKEN_ALREADY_USED);
            }
            Instant now = clock.instant();
            PricingService.Price price = pricing.price(new PricingRequest(tier.eventId(), tierId, quantity,
                    queue.token(tokenId).customerId(), tier.price(), now, queue.saleOpenedAt(tier.eventId())));
            reservation = new Reservation("R-" + nextReservation.getAndIncrement(), tokenId, tierId,
                    quantity, tier.price(), price.promotions(), price.total(), now);
            tier.hold(quantity);
            store.add(reservation);
        }

        // Announce it only after it is saved.
        bus.publish(new TicketHeld(reservation.reservationId(), tokenId, tierId,
                                   quantity, reservation.expiresAt()));
        return new HoldResult(HoldResult.Outcome.HELD, reservation);
    }

    private void onHoldExpired(HoldExpired event) {
        if (settled.add(event.reservationId())) {  // the same event twice is ignored
            tier(event.tierId()).release(event.quantity());
        }
    }

    private void onHoldReleased(HoldReleased event) {
        if (settled.add(event.reservationId())) {
            tier(event.tierId()).release(event.quantity());
        }
    }

    private void onOrderConfirmed(OrderConfirmed event) {
        if (settled.add(event.reservationId())) {
            tier(event.tierId()).sell(event.quantity());
        }
    }
}
