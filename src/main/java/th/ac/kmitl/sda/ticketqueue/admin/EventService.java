package th.ac.kmitl.sda.ticketqueue.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.inventory.InventoryService;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.queue.QueueService;

/** Chai creates an event and its tiers, opens the sale and sets the admission rate. */
public final class EventService {

    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong nextEvent = new AtomicLong(1);
    private final InventoryService inventory;
    private final QueueService queue;

    public EventService(InventoryService inventory, QueueService queue) {
        this.inventory = inventory;
        this.queue = queue;
    }

    /** Creates an event with its name, venue, date and sale-open time. */
    public Event createEvent(String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        Event event = new Event("E-" + nextEvent.getAndIncrement(), name, venue, date, saleOpensAt);
        events.put(event.eventId(), event);
        return event;
    }

    /** Adds a price tier. Its counters live in inventory, the only place that may change them. */
    public TicketTier addTier(String eventId, String name, Money price, int quantity) {
        return inventory.createTier(event(eventId).eventId(), name, price, quantity);
    }

    /** Opens the sale. A sale with nothing to sell is refused. */
    public void openSale(String eventId) {
        Event event = event(eventId);
        if (inventory.tiersOf(eventId).isEmpty()) {
            throw new IllegalStateException("Cannot open a sale with no tiers");
        }
        event.openSale();
        queue.openSale(eventId);
    }

    /** How many customers the waiting room lets in each second. */
    public void setAdmissionRate(String eventId, int perSecond) {
        queue.setAdmissionRate(event(eventId).eventId(), perSecond);
    }

    public Event event(String eventId) {
        Event event = events.get(eventId);
        if (event == null) {
            throw new IllegalArgumentException("No event " + eventId);
        }
        return event;
    }
}
