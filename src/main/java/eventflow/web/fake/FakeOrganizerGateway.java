package eventflow.web.fake;

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
import java.util.Locale;

/**
 * The organizer's rules (UC-05, UC-06, UC-07) over the fake's memory.
 * Throw-away: replaced by CoreOrganizerGateway when core/ is ready.
 */
public class FakeOrganizerGateway implements OrganizerGateway {

    private static final int MAX_TIERS = 8;

    private final FakeBookingGateway fake;

    public FakeOrganizerGateway(FakeBookingGateway fake) {
        this.fake = fake;
    }

    @Override
    public List<OrgEventRow> listEvents(String organizerId) {
        synchronized (fake) {
            fake.sweep();
            return fake.events.values().stream()
                    .filter(e -> e.owner.equals(organizerId))
                    .sorted(Comparator.comparingLong((FakeBookingGateway.Event e) -> e.dateTime).reversed())
                    .map(e -> new OrgEventRow(e.id, e.name, e.venue, e.dateTime, e.saleOpensAt, fake.orgState(e),
                            sold(e), total(e)))
                    .toList();
        }
    }

    @Override
    public OrgEvent createEvent(String organizerId, EventDetails details) {
        synchronized (fake) {
            fake.sweep();
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
            if (sale <= fake.now()) {
                throw GatewayException.invalid("SALE_IN_PAST", "saleOpensAt", "The sale must open in the future.");
            }
            if (sale >= date) {
                throw GatewayException.invalid("SALE_AFTER_EVENT", "saleOpensAt", "The sale must open before the event.");
            }
            int rate = details.admitPerSecond() == null ? FakeBookingGateway.ADMIT_PER_SECOND
                    : details.admitPerSecond();
            if (rate < 1) {
                throw GatewayException.invalid("RATE_TOO_LOW", "admitPerSecond", "Must be at least 1.");
            }
            String artwork = checkedArtwork(details.artwork());
            FakeBookingGateway.Event e = new FakeBookingGateway.Event(uniqueId(slug(name)), name, shortName(name),
                    venue, artwork, date, sale);
            e.draft = true;
            e.owner = organizerId;
            e.admitPerSecond = rate;
            fake.events.put(e.id, e);
            return view(e);
        }
    }

    @Override
    public OrgEvent getEvent(String organizerId, String eventId) {
        synchronized (fake) {
            fake.sweep();
            return view(owned(organizerId, eventId));
        }
    }

    @Override
    public OrgEvent updateDetails(String organizerId, String eventId, EventDetails details) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            String state = fake.orgState(e);
            if (!editable(state)) {
                throw locked();
            }
            String name = details.name() == null ? e.name : checkedName(details.name());
            String venue = details.venue() == null ? e.venue : checkedVenue(details.venue());
            long date = details.eventDateTime() == null ? e.dateTime : details.eventDateTime();
            long sale = e.saleOpensAt;
            if (details.saleOpensAt() != null && details.saleOpensAt() != e.saleOpensAt) {
                if (state.equals("ON_SALE")) {
                    throw GatewayException.conflict("EVENT_LOCKED", "saleOpensAt",
                            "The sale time cannot change once the sale is on.", null);
                }
                if (details.saleOpensAt() <= fake.now()) {
                    throw GatewayException.invalid("SALE_IN_PAST", "saleOpensAt", "The sale must open in the future.");
                }
                sale = details.saleOpensAt();
            }
            if (sale >= date) {
                throw GatewayException.invalid("SALE_AFTER_EVENT", "saleOpensAt", "The sale must open before the event.");
            }
            String artwork = details.artwork() == null ? e.artwork : checkedArtwork(details.artwork());
            e.name = name;
            e.venue = venue;
            e.dateTime = date;
            e.saleOpensAt = sale;
            e.artwork = artwork;
            return view(e);
        }
    }

    @Override
    public OrgEvent replaceTiers(String organizerId, String eventId, List<TierInput> tiers) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            if (!e.draft) {
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
            List<FakeBookingGateway.Tier> built = new ArrayList<>();
            for (TierInput in : tiers) {
                String name = in.name().trim();
                built.add(new FakeBookingGateway.Tier(uniqueTierId(built, slug(name)), name, in.price(), in.total(), 0));
            }
            e.tiers.clear();
            e.tiers.addAll(built);
            return view(e);
        }
    }

    @Override
    public OrgEvent addTier(String organizerId, String eventId, TierInput tier) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            if (!editable(fake.orgState(e))) {
                throw locked();
            }
            if (tier == null) {
                throw GatewayException.invalid("REQUIRED", "tier", "Required");
            }
            if (e.tiers.size() >= MAX_TIERS) {
                throw GatewayException.invalid("TOO_MANY_TIERS", "tiers", "Up to 8 tiers.");
            }
            checkRow(0, "", tier.name(), tier.price(), tier.total());
            String name = tier.name().trim();
            e.tiers.add(new FakeBookingGateway.Tier(uniqueTierId(e.tiers, slug(name)), name, tier.price(),
                    tier.total(), 0));
            return view(e);
        }
    }

    @Override
    public OrgEvent updateTier(String organizerId, String eventId, String tierId, TierInput tier) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            if (!editable(fake.orgState(e))) {
                throw locked();
            }
            if (tier == null) {
                throw GatewayException.invalid("REQUIRED", "tier", "Required");
            }
            FakeBookingGateway.Tier t = e.tier(tierId);
            String name = tier.name() == null ? t.name : tier.name().trim();
            Long price = tier.price() == null ? t.price : tier.price();
            Integer total = tier.total() == null ? t.total : tier.total();
            checkRow(0, "", name, price, total);
            if (fake.orgState(e).equals("ON_SALE") && price != t.price) {
                throw GatewayException.conflict("PRICE_FROZEN", "price",
                        "Prices are frozen while the sale is on, so no customer sees a price change mid-purchase.",
                        null);
            }
            if (total < t.held + t.sold) {
                throw GatewayException.conflict("BELOW_HELD_SOLD", "total",
                        String.format(Locale.US, "%s: %,d sold and %,d held. Cannot reduce below %,d.",
                                name, t.sold, t.held, t.held + t.sold), t.held + t.sold);
            }
            t.name = name;
            t.price = price;
            t.total = total;
            return view(e);
        }
    }

    @Override
    public OrgEvent publish(String organizerId, String eventId) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            if (!e.draft) {
                throw notDraft();
            }
            if (e.tiers.isEmpty()) {
                throw GatewayException.conflict("NO_TIERS", null,
                        "An event needs at least one tier before it can be published.", null);
            }
            e.draft = false;
            return view(e);
        }
    }

    @Override
    public OrgEvent setRate(String organizerId, String eventId, Integer admitPerSecond) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            if (!editable(fake.orgState(e))) {
                throw locked();
            }
            if (admitPerSecond == null || admitPerSecond < 1) {
                throw GatewayException.invalid("RATE_TOO_LOW", "admitPerSecond", "Must be at least 1.");
            }
            e.admitPerSecond = admitPerSecond;
            return view(e);
        }
    }

    @Override
    public OrgEvent closeSale(String organizerId, String eventId) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            if (!fake.orgState(e).equals("ON_SALE")) {
                throw GatewayException.conflict("NOT_ON_SALE", null, "Only a sale that is on can be closed.", null);
            }
            e.closing = true;
            return view(e);
        }
    }

    @Override
    public Dashboard dashboard(String organizerId, String eventId) {
        synchronized (fake) {
            fake.sweep();
            FakeBookingGateway.Event e = owned(organizerId, eventId);
            List<OrgTier> rows = tiers(e);
            int total = total(e);
            int held = held(e);
            int sold = sold(e);
            OrgTier all = new OrgTier("all", "All", 0, total, held, sold, total - held - sold);
            return new Dashboard(e.id, e.name, fake.orgState(e), fake.waiting(e), e.admitPerSecond, rows, all,
                    fake.revenue(e), e.closedAt);
        }
    }

    // ---------- the shared bits ----------

    private FakeBookingGateway.Event owned(String organizerId, String eventId) {
        FakeBookingGateway.Event e = fake.events.get(eventId);
        if (e == null || !e.owner.equals(organizerId)) {
            throw GatewayException.notFound("Event");
        }
        return e;
    }

    static final int MAX_ARTWORK_CHARS = 2_800_000; // a 2 MB picture as base64

    /** Null or "" means no picture. Otherwise a PNG, JPG or WebP data URL of at most 2 MB. */
    static String checkedArtwork(String artwork) {
        if (artwork == null || artwork.isEmpty()) {
            return null;
        }
        if (!artwork.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+") || artwork.length() > MAX_ARTWORK_CHARS) {
            throw GatewayException.invalid("BAD_IMAGE", "artwork", "Use a PNG, JPG or WebP picture of 2 MB or less.");
        }
        return artwork;
    }

    private OrgEvent view(FakeBookingGateway.Event e) {
        return new OrgEvent(e.id, e.name, e.venue, e.dateTime, e.saleOpensAt, fake.orgState(e), e.admitPerSecond,
                tiers(e), held(e), fake.waiting(e), e.closedAt, e.artwork);
    }

    private static List<OrgTier> tiers(FakeBookingGateway.Event e) {
        return e.tiers.stream()
                .map(t -> new OrgTier(t.id, t.name, t.price, t.total, t.held, t.sold, t.remaining()))
                .toList();
    }

    private static int held(FakeBookingGateway.Event e) {
        return e.tiers.stream().mapToInt(t -> t.held).sum();
    }

    private static int sold(FakeBookingGateway.Event e) {
        return e.tiers.stream().mapToInt(t -> t.sold).sum();
    }

    private static int total(FakeBookingGateway.Event e) {
        return e.tiers.stream().mapToInt(t -> t.total).sum();
    }

    private static boolean editable(String state) {
        return state.equals("DRAFT") || state.equals("SCHEDULED") || state.equals("ON_SALE");
    }

    private static GatewayException locked() {
        return GatewayException.conflict("EVENT_LOCKED", null, "This event can no longer be changed.", null);
    }

    private static GatewayException notDraft() {
        return GatewayException.conflict("NOT_DRAFT", null, "Only a draft can do this.", null);
    }

    private static String checkedName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw GatewayException.invalid("REQUIRED", "name", "Required");
        }
        if (value.length() > 40) {
            throw GatewayException.invalid("TOO_LONG", "name", "Up to 40 characters");
        }
        return value;
    }

    private static String checkedVenue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw GatewayException.invalid("REQUIRED", "venue", "Required");
        }
        if (value.length() > 30) {
            throw GatewayException.invalid("TOO_LONG", "venue", "Up to 30 characters");
        }
        return value;
    }

    private static void checkRow(int index, String prefix, String rawName, Long price, Integer total) {
        String label = rawName == null || rawName.trim().isEmpty() ? "Tier " + (index + 1) : rawName.trim();
        if (rawName == null || rawName.trim().isEmpty()) {
            throw GatewayException.invalid("TIER_NAME_REQUIRED", prefix + "name", label + " · Name is required.");
        }
        if (price == null || price < 0) {
            throw GatewayException.invalid("PRICE_NEGATIVE", prefix + "price", label + " · Price cannot be below ฿0.");
        }
        if (total == null || total <= 0) {
            throw GatewayException.invalid("SEATS_ZERO", prefix + "total", label + " · Seats must be more than 0.");
        }
    }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (fake.events.containsKey(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static String uniqueTierId(List<FakeBookingGateway.Tier> existing, String base) {
        String id = base;
        int n = 2;
        while (taken(existing, id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static boolean taken(List<FakeBookingGateway.Tier> tiers, String id) {
        for (FakeBookingGateway.Tier t : tiers) {
            if (t.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String slug(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "event" : s;
    }

    private static String shortName(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.endsWith(" BANGKOK") ? upper.substring(0, upper.length() - " BANGKOK".length()) : upper;
    }
}
