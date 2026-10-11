package eventflow;

import java.time.Clock;

import eventflow.admin.EventService;
import eventflow.core.event.EventBus;
import eventflow.core.inventory.InventoryService;
import eventflow.core.order.CheckoutService;
import eventflow.core.order.HoldExpiryScheduler;
import eventflow.core.order.ReservationService;
import eventflow.core.order.ReservationStore;
import eventflow.core.queue.AdmissionScheduler;
import eventflow.core.queue.QueueService;
import eventflow.core.registry.PluginRegistry;
import eventflow.plugin.payment.PromptPayPlugin;

// Builds the whole system. The only class that knows plug-in classes.
public final class EventFlow {

    private final EventBus bus = new EventBus();
    private final PluginRegistry registry = new PluginRegistry();
    private final ReservationStore store = new ReservationStore();
    private final QueueService queue;
    private final AdmissionScheduler admission;
    private final InventoryService inventory;
    private final ReservationService reservations;
    private final CheckoutService checkout;
    private final HoldExpiryScheduler expiry;
    private final EventService events;

    public EventFlow(Clock clock) {
        registry.register(new PromptPayPlugin());  // add a new payment method here

        queue = new QueueService(bus, clock, this::soldOut);
        admission = new AdmissionScheduler(queue, bus, clock);
        inventory = new InventoryService(store, queue, bus, clock);
        reservations = new ReservationService(store, bus, registry, clock);
        checkout = new CheckoutService(store, reservations, registry, bus, clock);
        expiry = new HoldExpiryScheduler(store, reservations);
        events = new EventService(inventory, queue);
    }

    private boolean soldOut(String eventId) {
        return inventory.soldOut(eventId);
    }

    public EventBus bus()                   { return bus; }
    public PluginRegistry registry()        { return registry; }
    public ReservationStore store()         { return store; }
    public QueueService queue()             { return queue; }
    public AdmissionScheduler admission()   { return admission; }
    public InventoryService inventory()     { return inventory; }
    public ReservationService reservations() { return reservations; }
    public CheckoutService checkout()       { return checkout; }
    public HoldExpiryScheduler expiry()     { return expiry; }
    public EventService events()            { return events; }
}
