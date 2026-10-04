package th.ac.kmitl.sda.ticketqueue.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.inventory.InventoryService;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;

/** UC-05 · Chai creates an event and its tiers, then opens the sale. */
public final class EventService {

    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong nextEvent = new AtomicLong(1);
    private final InventoryService inventory;

    public EventService(InventoryService inventory) {
        this.inventory = inventory;
    }

    /** FR-23. */
    public Event createEvent(String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        Event event = new Event("E-" + nextEvent.getAndIncrement(), name, venue, date, saleOpensAt);
        events.put(event.eventId(), event);
        return event;
    }

    /** FR-24. The tier's counters live in inventory, the only place that may change them. */
    public TicketTier addTier(String eventId, String name, Money price, int quantity) {
        return inventory.createTier(event(eventId).eventId(), name, price, quantity);
    }

    /** FR-25 · a sale with nothing to sell is refused. */
    public void openSale(String eventId) {
        Event event = event(eventId);
        if (inventory.tiersOf(eventId).isEmpty()) {
            throw new IllegalStateException("Cannot open a sale with no tiers (FR-25)");
        }
        event.openSale();
    }

    public Event event(String eventId) {
        Event event = events.get(eventId);
        if (event == null) {
            throw new IllegalArgumentException("No event " + eventId);
        }
        return event;
    }
}
