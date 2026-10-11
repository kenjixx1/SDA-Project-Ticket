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

public final class EventService {

    private final Map<String, Event> events = new ConcurrentHashMap<>();
    private final AtomicLong nextEvent = new AtomicLong(1);
    private final InventoryService inventory;
    private final QueueService queue;

    public EventService(InventoryService inventory, QueueService queue) {
        this.inventory = inventory;
        this.queue = queue;
    }

    public Event createEvent(String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        Event event = new Event("E-" + nextEvent.getAndIncrement(), name, venue, date, saleOpensAt);
        events.put(event.eventId(), event);
        return event;
    }

    public TicketTier addTier(String eventId, String name, Money price, int quantity) {
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
