package th.ac.kmitl.sda.ticketqueue;

import java.time.Clock;

import th.ac.kmitl.sda.ticketqueue.admin.EventService;
import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.inventory.InventoryService;
import th.ac.kmitl.sda.ticketqueue.core.order.CheckoutService;
import th.ac.kmitl.sda.ticketqueue.core.order.HoldExpiryScheduler;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationService;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationStore;
import th.ac.kmitl.sda.ticketqueue.core.registry.PluginRegistry;
import th.ac.kmitl.sda.ticketqueue.plugin.payment.PromptPayPlugin;

/**
 * Wires the whole system together. This is the only class that names a
 * plug-in, and it sits outside {@code core/} on purpose (NFR-08).
 *
 * <p>Adding a payment method = write the plug-in, add one
 * {@code registry.register(...)} line here. Zero core files change.
 */
public final class EventFlow {

    private final EventBus bus = new EventBus();
    private final PluginRegistry registry = new PluginRegistry();
    private final ReservationStore store = new ReservationStore();
    private final InventoryService inventory;
    private final ReservationService reservations;
    private final CheckoutService checkout;
    private final HoldExpiryScheduler expiry;
    private final EventService events;

    public EventFlow(Clock clock) {
        registry.register(new PromptPayPlugin());

        inventory = new InventoryService(store, bus, clock);
        reservations = new ReservationService(store, bus, registry, clock);
        checkout = new CheckoutService(store, reservations, registry, bus, clock);
        expiry = new HoldExpiryScheduler(store, reservations);
        events = new EventService(inventory);
    }

    public EventBus bus()                   { return bus; }
    public PluginRegistry registry()        { return registry; }
    public ReservationStore store()         { return store; }
    public InventoryService inventory()     { return inventory; }
    public ReservationService reservations() { return reservations; }
    public CheckoutService checkout()       { return checkout; }
    public HoldExpiryScheduler expiry()     { return expiry; }
    public EventService events()            { return events; }
}
