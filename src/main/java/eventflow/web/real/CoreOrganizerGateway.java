package eventflow.web.real;

import eventflow.contract.Money;
import eventflow.core.inventory.TicketTier;
import eventflow.core.queue.QueueService;
import eventflow.web.GatewayException;
import eventflow.web.OrganizerGateway;
import eventflow.web.dto.OrgRequests.EventDetails;
import eventflow.web.dto.OrgRequests.TierInput;
import eventflow.web.dto.OrgViews.Dashboard;
import eventflow.web.dto.OrgViews.OrgEvent;
import eventflow.web.dto.OrgViews.OrgEventRow;
import eventflow.web.dto.OrgViews.OrgTier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static eventflow.web.OrgChecks.MAX_TIERS;
import static eventflow.web.OrgChecks.checkRow;
import static eventflow.web.OrgChecks.checkedArtwork;
import static eventflow.web.OrgChecks.checkedName;
import static eventflow.web.OrgChecks.checkedVenue;
import static eventflow.web.OrgChecks.shortName;
import static eventflow.web.OrgChecks.slug;

/**
 * The organizer console on the real booking engine. A draft lives only here; core first sees the
 * event on publish. Edits core cannot make yet are refused with NOT_IN_CORE.
 */
public final class CoreOrganizerGateway implements OrganizerGateway {

    private final RealEngine engine;

    public CoreOrganizerGateway(RealEngine engine) {
        this.engine = engine;
    }

    @Override
    public List<OrgEventRow> listEvents(String organizerId) {                                 // O-01
        synchronized (engine) {
            engine.tick();
            return engine.events.values().stream()
                    .filter(e -> e.owner.equals(organizerId))
                    .sorted(Comparator.comparingLong((RealEngine.EventInfo e) -> e.dateTime).reversed())
                    .map(e -> {
                        List<OrgTier> t = tiers(e);
                        return new OrgEventRow(e.id, e.name, e.venue, e.dateTime, e.saleOpensAt, engine.state(e),
                                t.stream().mapToInt(OrgTier::sold).sum(), t.stream().mapToInt(OrgTier::total).sum());
                    })
                    .toList();
        }
    }

    @Override
    public OrgEvent createEvent(String organizerId, EventDetails details) {                  // O-02
        synchronized (engine) {
            engine.tick();
            String name = checkedName(details.name());
            String venue = checkedVenue(details.venue());
            if (details.eventDateTime() == null) {
                throw GatewayException.invalid("REQUIRED", "eventDateTime", "Required");
            }
            if (details.saleOpensAt() == null) {
                throw GatewayException.invalid("REQUIRED", "saleOpensAt", "Required");
            }
            long date = details.eventDateTime();
            long sale = details.saleOpensAt();
            checkSale(sale, date);
            int rate = details.admitPerSecond() == null ? QueueService.DEFAULT_ADMISSION_RATE
                    : details.admitPerSecond();
            if (rate < 1) {
                throw GatewayException.invalid("RATE_TOO_LOW", "admitPerSecond", "Must be at least 1.");
            }
            RealEngine.EventInfo e = new RealEngine.EventInfo(uniqueId(slug(name)));
            e.name = name;
            e.shortName = shortName(name);
            e.venue = venue;
            e.artwork = checkedArtwork(details.artwork());
            e.dateTime = date;
            e.saleOpensAt = sale;
            e.owner = organizerId;
            e.admitPerSecond = rate;
            engine.events.put(e.id, e);
            return view(e);
        }
    }

    @Override
    public OrgEvent getEvent(String organizerId, String eventId) {                           // O-03
        synchronized (engine) {
            engine.tick();
            return view(owned(organizerId, eventId));
        }
    }

    @Override
    public OrgEvent updateDetails(String organizerId, String eventId, EventDetails details) {  // O-04
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (!e.draft()) {
                boolean changes = changed(details.name(), e.name) || changed(details.venue(), e.venue)
                        || changed(details.eventDateTime(), e.dateTime) || changed(details.saleOpensAt(), e.saleOpensAt);
                if (changes) {
                    throw RealEngine.notInCore("Changing a published event");
                }
                if (details.artwork() != null) {
                    e.artwork = checkedArtwork(details.artwork());
                }
                return view(e);
            }
            String name = details.name() == null ? e.name : checkedName(details.name());
            String venue = details.venue() == null ? e.venue : checkedVenue(details.venue());
            long date = details.eventDateTime() == null ? e.dateTime : details.eventDateTime();
            long sale = details.saleOpensAt() == null ? e.saleOpensAt : details.saleOpensAt();
            if (details.saleOpensAt() != null && details.saleOpensAt() != e.saleOpensAt) {
                checkSale(sale, date);
            } else if (sale >= date) {
                throw GatewayException.invalid("SALE_AFTER_EVENT", "saleOpensAt", "The sale must open before the event.");
            }
            String artwork = details.artwork() == null ? e.artwork : checkedArtwork(details.artwork());
            e.name = name;
            e.shortName = shortName(name);
            e.venue = venue;
            e.dateTime = date;
            e.saleOpensAt = sale;
            e.artwork = artwork;
            return view(e);
        }
    }

    @Override
    public OrgEvent replaceTiers(String organizerId, String eventId, List<TierInput> tiers) {  // O-05
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (!e.draft()) {
                throw notDraft();
            }
            if (tiers == null) {
                throw GatewayException.invalid("REQUIRED", "tiers", "Required");
            }
            if (tiers.size() > MAX_TIERS) {
                throw GatewayException.invalid("TOO_MANY_TIERS", "tiers", "Up to 8 tiers.");
            }
            for (int i = 0; i < tiers.size(); i++) {
                TierInput in = tiers.get(i);
                if (in == null) {
                    throw GatewayException.invalid("REQUIRED", "tiers[" + i + "]", "Required");
                }
                checkRow(i, "tiers[" + i + "].", in.name(), in.price(), in.total());
            }
            List<RealEngine.DraftTier> built = new ArrayList<>();
            for (TierInput in : tiers) {
                String name = in.name().trim();
                built.add(new RealEngine.DraftTier(uniqueTierId(built, slug(name)), name, in.price(), in.total()));
            }
            e.draftTiers.clear();
            e.draftTiers.addAll(built);
            return view(e);
        }
    }

    @Override
    public OrgEvent addTier(String organizerId, String eventId, TierInput tier) {            // O-06
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (tier == null) {
                throw GatewayException.invalid("REQUIRED", "tier", "Required");
            }
            if (tiers(e).size() >= MAX_TIERS) {
                throw GatewayException.invalid("TOO_MANY_TIERS", "tiers", "Up to 8 tiers.");
            }
            checkRow(0, "", tier.name(), tier.price(), tier.total());
            String name = tier.name().trim();
            if (e.draft()) {
                e.draftTiers.add(new RealEngine.DraftTier(uniqueTierId(e.draftTiers, slug(name)), name,
                        tier.price(), tier.total()));
            } else {
                engine.flow.events().addTier(e.coreId, name, Money.baht(tier.price()), tier.total());
            }
            return view(e);
        }
    }

    @Override
    public OrgEvent updateTier(String organizerId, String eventId, String tierId, TierInput tier) {  // O-07
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (tier == null) {
                throw GatewayException.invalid("REQUIRED", "tier", "Required");
            }
            if (!e.draft()) {
                throw RealEngine.notInCore("Changing a tier after publishing");
            }
            RealEngine.DraftTier t = e.draftTiers.stream().filter(x -> x.id.equals(tierId)).findFirst()
                    .orElseThrow(() -> GatewayException.notFound("Tier"));
            String name = tier.name() == null ? t.name : tier.name().trim();
            Long price = tier.price() == null ? t.price : tier.price();
            Integer total = tier.total() == null ? t.total : tier.total();
            checkRow(0, "", name, price, total);
            t.name = name;
            t.price = price;
            t.total = total;
            return view(e);
        }
    }

    @Override
    public OrgEvent publish(String organizerId, String eventId) {                            // O-08
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (!e.draft()) {
                throw notDraft();
            }
            if (e.draftTiers.isEmpty()) {
                throw GatewayException.conflict("NO_TIERS", null,
                        "An event needs at least one tier before it can be published.", null);
            }
            engine.publish(e);
            return view(e);
        }
    }

    @Override
    public OrgEvent setRate(String organizerId, String eventId, Integer admitPerSecond) {     // O-09
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            if (admitPerSecond == null || admitPerSecond < 1) {
                throw GatewayException.invalid("RATE_TOO_LOW", "admitPerSecond", "Must be at least 1.");
            }
            e.admitPerSecond = admitPerSecond;
            if (!e.draft()) {
                engine.flow.queue().setAdmissionRate(e.coreId, admitPerSecond);
            }
            return view(e);
        }
    }

    @Override
    public OrgEvent closeSale(String organizerId, String eventId) {                          // O-10
        synchronized (engine) {
            engine.tick();
            owned(organizerId, eventId);
            throw RealEngine.notInCore("Closing a sale");
        }
    }

    @Override
    public Dashboard dashboard(String organizerId, String eventId) {                         // O-11, stand-in
        synchronized (engine) {
            engine.tick();
            RealEngine.EventInfo e = owned(organizerId, eventId);
            List<OrgTier> rows = tiers(e);
            int total = rows.stream().mapToInt(OrgTier::total).sum();
            int held = rows.stream().mapToInt(OrgTier::held).sum();
            int sold = rows.stream().mapToInt(OrgTier::sold).sum();
            OrgTier all = new OrgTier("all", "All", 0, total, held, sold, total - held - sold);
            long revenue = engine.orders.values().stream().filter(o -> o.eventId.equals(e.id))
                    .mapToLong(o -> o.paid).sum();
            return new Dashboard(e.id, e.name, engine.state(e), waiting(e), e.admitPerSecond, rows, all, revenue, null);
        }
    }

    // ---------- helpers ----------

    private RealEngine.EventInfo owned(String organizerId, String eventId) {
        RealEngine.EventInfo e = engine.events.get(eventId);
        if (e == null || !e.owner.equals(organizerId)) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    private OrgEvent view(RealEngine.EventInfo e) {
        List<OrgTier> rows = tiers(e);
        return new OrgEvent(e.id, e.name, e.venue, e.dateTime, e.saleOpensAt, engine.state(e), e.admitPerSecond,
                rows, rows.stream().mapToInt(OrgTier::held).sum(), waiting(e), null, e.artwork);
    }

    private List<OrgTier> tiers(RealEngine.EventInfo e) {
        if (e.draft()) {
            return e.draftTiers.stream()
                    .map(t -> new OrgTier(t.id, t.name, t.price, t.total, 0, 0, t.total)).toList();
        }
        return engine.coreTiers(e).stream()
                .map((TicketTier t) -> new OrgTier(t.tierId(), t.name(), t.price().baht(), t.totalQuantity(),
                        t.heldQuantity(), t.soldQuantity(), t.remaining()))
                .toList();
    }

    private int waiting(RealEngine.EventInfo e) {
        return engine.state(e).equals("ON_SALE") ? engine.flow.queue().queueLength(e.coreId) : 0;
    }

    private void checkSale(long sale, long date) {
        if (sale <= engine.clock.now()) {
            throw GatewayException.invalid("SALE_IN_PAST", "saleOpensAt", "The sale must open in the future.");
        }
        if (sale >= date) {
            throw GatewayException.invalid("SALE_AFTER_EVENT", "saleOpensAt", "The sale must open before the event.");
        }
    }

    private static boolean changed(Object given, Object current) {
        if (given instanceof String s) {
            return !s.trim().equals(current);
        }
        return given != null && !Objects.equals(given, current);
    }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (engine.events.containsKey(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static String uniqueTierId(List<RealEngine.DraftTier> existing, String base) {
        String id = base;
        int n = 2;
        while (taken(existing, id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static boolean taken(List<RealEngine.DraftTier> tiers, String id) {
        return tiers.stream().anyMatch(t -> t.id.equals(id));
    }

    private static GatewayException notDraft() {
        return GatewayException.conflict("NOT_DRAFT", null, "Only a draft can do this.", null);
    }
}
