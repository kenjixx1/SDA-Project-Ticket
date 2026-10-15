package eventflow.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import eventflow.contract.Money;
import eventflow.core.event.EventBus;
import eventflow.core.event.SaleClosed;
import eventflow.core.inventory.BelowHeldSoldException;
import eventflow.core.inventory.InventoryService;
import eventflow.core.inventory.TicketTier;
import eventflow.core.queue.QueueService;

// Chai creates and manages events. Each change is checked against the event's state first.
public final class EventService {

    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong nextEvent = new AtomicLong(1);
    private final InventoryService inventory;
    private final QueueService queue;
    private final EventBus bus;
    private final Clock clock;

    public EventService(InventoryService inventory, QueueService queue, EventBus bus, Clock clock) {
        this.inventory = inventory;
        this.queue = queue;
        this.bus = bus;
        this.clock = clock;
    }

    public Event createEvent(String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        Event event = new Event("E-" + nextEvent.getAndIncrement(), name, venue, date, saleOpensAt);
        events.put(event.eventId(), event);
        return event;
    }

    public TicketTier addTier(String eventId, String name, Money price, int quantity) {
        refuseIfLocked(eventId);
        return inventory.createTier(event(eventId).eventId(), name, price, quantity);
    }

    public void openSale(String eventId) {
        Event event = event(eventId);
        if (inventory.tiersOf(eventId).isEmpty()) {
            throw new IllegalStateException("Cannot open a sale with no tiers");
        }
        event.openSale();
        queue.openSale(eventId);
    }

    public void setAdmissionRate(String eventId, int perSecond) {
        refuseIfLocked(eventId);
        queue.setAdmissionRate(event(eventId).eventId(), perSecond);
    }

    // Name, venue and date can change until the sale closes. The sale time cannot change once it is on.
    public void changeDetails(String eventId, String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        Event event = event(eventId);
        EventState state = refuseIfLocked(eventId);
        if (state == EventState.ON_SALE && !Objects.equals(saleOpensAt, event.saleOpensAt())) {
            throw new ChangeRefused(ChangeRefused.Reason.SALE_TIME_LOCKED,
                    "The sale time cannot change once the sale is on.");
        }
        event.changeDetails(name, venue, date, saleOpensAt);
    }

    // Prices freeze when the sale opens. Seats can be added any time, but never cut below held + sold.
    public void changeTier(String eventId, String tierId, String name, Money price, int total) {
        EventState state = refuseIfLocked(eventId);
        TicketTier tier = tierOf(eventId, tierId);
        if (state == EventState.ON_SALE && !price.equals(tier.price())) {
            throw new ChangeRefused(ChangeRefused.Reason.PRICE_FROZEN,
                    "Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.");
        }
        try {
            inventory.resizeTier(tierId, total);
        } catch (BelowHeldSoldException e) {
            throw new ChangeRefused(ChangeRefused.Reason.BELOW_HELD_SOLD, String.format(
                    "%s: %,d sold and %,d held. Cannot reduce below %,d.", name, e.sold(), e.held(),
                    e.held() + e.sold()), e.held(), e.sold());
        }
        inventory.repriceTier(tierId, price);
        inventory.renameTier(tierId, name);
    }

    // No new fans are let in. Holds already running keep their time and may still be paid.
    public void closeSale(String eventId) {
        if (state(eventId) != EventState.ON_SALE) {
            throw new ChangeRefused(ChangeRefused.Reason.NOT_ON_SALE, "Only a sale that is on can be closed.");
        }
        event(eventId).startClosing();
        bus.publish(new SaleClosed(eventId));
    }

    // A closing sale becomes CLOSED once no seat is held any more.
    public EventState state(String eventId) {
        Event event = event(eventId);
        if (event.closedAt() != null) {
            return EventState.CLOSED;
        }
        if (event.closing()) {
            if (inventory.tiersOf(eventId).stream().anyMatch(t -> t.heldQuantity() > 0)) {
                return EventState.CLOSING;
            }
            event.markClosed(clock.instant());
            return EventState.CLOSED;
        }
        return event.onSale() ? EventState.ON_SALE : EventState.SCHEDULED;
    }

    public Event event(String eventId) {
        Event event = events.get(eventId);
        if (event == null) {
            throw new IllegalArgumentException("No event " + eventId);
        }
        return event;
    }

    private EventState refuseIfLocked(String eventId) {
        EventState state = state(eventId);
        if (state == EventState.CLOSING || state == EventState.CLOSED) {
            throw new ChangeRefused(ChangeRefused.Reason.EVENT_LOCKED, "This event can no longer be changed.");
        }
        return state;
    }

    private TicketTier tierOf(String eventId, String tierId) {
        TicketTier tier = inventory.tier(tierId);
        if (!tier.eventId().equals(eventId)) {
            throw new IllegalArgumentException("Tier " + tierId + " is not in event " + eventId);
        }
        return tier;
    }
}
