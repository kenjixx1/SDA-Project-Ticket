package th.ac.kmitl.sda.ticketqueue.core.inventory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.event.HoldExpired;
import th.ac.kmitl.sda.ticketqueue.core.event.OrderConfirmed;
import th.ac.kmitl.sda.ticketqueue.core.event.TicketHeld;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationStore;

/**
 * The ONLY writer of {@code heldQuantity} and {@code soldQuantity} (INV-01).
 *
 * <p>Takes the atomic hold and publishes {@link TicketHeld}. Listens to
 * {@link HoldExpired} (seats go back) and {@link OrderConfirmed} (held → sold).
 */
public final class InventoryService {

    /** FR-11 · ASM-02. */
    public static final int ORDER_LIMIT = 4;

    private final Map<String, TicketTier> tiers = new ConcurrentHashMap<>();
    private final Set<String> usedTokens = ConcurrentHashMap.newKeySet();
    private final Set<String> settled = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextReservation = new AtomicLong(1001);
    private final AtomicLong nextTier = new AtomicLong(1);
    private final ReservationStore store;
    private final EventBus bus;
    private final Clock clock;

    public InventoryService(ReservationStore store, EventBus bus, Clock clock) {
        this.store = store;
        this.bus = bus;
        this.clock = clock;
        bus.subscribe(HoldExpired.class, this::onHoldExpired);
        bus.subscribe(OrderConfirmed.class, this::onOrderConfirmed);
    }

    public TicketTier createTier(String eventId, String name, Money price, int totalQuantity) {
        TicketTier tier = new TicketTier("T-" + nextTier.getAndIncrement(),
                                         eventId, name, price, totalQuantity);
        tiers.put(tier.tierId(), tier);
        return tier;
    }

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

    /**
     * UC-02 · the atomic hold (R-E04-1).
     *
     * <p>Checking what is left, counting the seats as held and saving the
     * reservation all happen inside one lock on the tier. No two requests can
     * both read "10 left" before either writes. 2 or 0 — never 1 (R-E04-2).
     */
    public HoldResult hold(String tokenId, String tierId, int quantity) {
        if (quantity < 1 || quantity > ORDER_LIMIT) {
            return HoldResult.refused(HoldResult.Outcome.OVER_ORDER_LIMIT);
        }
        TicketTier tier = tier(tierId);
        Reservation reservation;
        synchronized (tier) {
            if (tier.remaining() < quantity) {
                return HoldResult.refused(HoldResult.Outcome.NOT_ENOUGH_SEATS);
            }
            if (!usedTokens.add(tokenId)) {
                return HoldResult.refused(HoldResult.Outcome.TOKEN_ALREADY_USED);
            }
            reservation = new Reservation("R-" + nextReservation.getAndIncrement(), tokenId,
                    tierId, quantity, tier.price(), clock.instant());
            tier.hold(quantity);
            store.add(reservation);
        }
        // Saved first, announced second.
        bus.publish(new TicketHeld(reservation.reservationId(), tokenId, tierId,
                                   quantity, reservation.expiresAt()));
        return new HoldResult(HoldResult.Outcome.HELD, reservation);
    }

    /** R-E04-5 · the seats are back on sale. */
    private void onHoldExpired(HoldExpired event) {
        if (settled.add(event.reservationId())) {  // idempotent: twice = once
            tier(event.tierId()).release(event.quantity());
        }
    }

    /** R-E04-4 · held goes down and sold goes up by the same amount, in one step. */
    private void onOrderConfirmed(OrderConfirmed event) {
        if (settled.add(event.reservationId())) {
            tier(event.tierId()).sell(event.quantity());
        }
    }
}
