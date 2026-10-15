package eventflow.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import eventflow.contract.Money;
import eventflow.core.event.EventBus;
import eventflow.core.event.OrderConfirmed;
import eventflow.core.event.UserAdmitted;
import eventflow.core.inventory.InventoryService;
import eventflow.core.inventory.TicketTier;
import eventflow.core.queue.QueueService;

// Chai's live view of a sale. Revenue and admissions come from listening to events.
public final class SalesDashboardService {

    public record TierRow(String tierId, String name, Money price, int total, int held, int sold, int remaining) {
    }

    public record Dashboard(String eventId, EventState state, int waiting, int admissionRate, long admitted,
                            List<TierRow> tiers, TierRow all, Money revenue, long ordersConfirmed,
                            Instant closedAt) {
    }

    private final Map<String, AtomicLong> revenueBaht = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> orders = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> admitted = new ConcurrentHashMap<>();
    private final InventoryService inventory;
    private final QueueService queue;
    private final EventService events;

    public SalesDashboardService(EventBus bus, InventoryService inventory, QueueService queue, EventService events) {
        this.inventory = inventory;
        this.queue = queue;
        this.events = events;
        bus.subscribe(OrderConfirmed.class, this::onOrderConfirmed);
        bus.subscribe(UserAdmitted.class, e -> counter(admitted, e.eventId()).incrementAndGet());
    }

    public Dashboard dashboard(String eventId) {
        List<TierRow> tiers = inventory.tiersOf(eventId).stream().map(SalesDashboardService::row).toList();
        int total = tiers.stream().mapToInt(TierRow::total).sum();
        int held = tiers.stream().mapToInt(TierRow::held).sum();
        int sold = tiers.stream().mapToInt(TierRow::sold).sum();
        TierRow all = new TierRow("all", "All", Money.baht(0), total, held, sold, total - held - sold);
        return new Dashboard(eventId, events.state(eventId), queue.queueLength(eventId),
                queue.admissionRate(eventId), counter(admitted, eventId).get(), tiers, all,
                Money.baht(counter(revenueBaht, eventId).get()), counter(orders, eventId).get(),
                events.event(eventId).closedAt());
    }

    // Confirmed orders only: a hold is not money yet.
    private void onOrderConfirmed(OrderConfirmed order) {
        String eventId = inventory.tier(order.tierId()).eventId();
        counter(revenueBaht, eventId).addAndGet(order.totalAmount().baht());
        counter(orders, eventId).incrementAndGet();
    }

    private static TierRow row(TicketTier t) {
        return new TierRow(t.tierId(), t.name(), t.price(), t.totalQuantity(), t.heldQuantity(),
                t.soldQuantity(), t.remaining());
    }

    private static AtomicLong counter(Map<String, AtomicLong> map, String eventId) {
        return map.computeIfAbsent(eventId, id -> new AtomicLong());
    }
}
