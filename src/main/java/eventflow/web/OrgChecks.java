package eventflow.web;

import java.util.Locale;

/** The organizer's input rules (UC-05), shared by the fake and the core gateways so both refuse the same way. */
public final class OrgChecks {

    private OrgChecks() {
    }

    public static final int MAX_TIERS = 8;
    public static final int MAX_ARTWORK_CHARS = 2_800_000; // a 2 MB picture as base64

    /** Null or "" means no picture. Otherwise a PNG, JPG or WebP data URL of at most 2 MB. */
    public static String checkedArtwork(String artwork) {
        if (artwork == null || artwork.isEmpty()) {
            return null;
        }
        if (!artwork.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+") || artwork.length() > MAX_ARTWORK_CHARS) {
            throw GatewayException.invalid("BAD_IMAGE", "artwork", "Use a PNG, JPG or WebP picture of 2 MB or less.");
        }
        return artwork;
    }

    public static String checkedName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw GatewayException.invalid("REQUIRED", "name", "Required");
        }
        if (value.length() > 40) {
            throw GatewayException.invalid("TOO_LONG", "name", "Up to 40 characters");
        }
        return value;
    }

    public static String checkedVenue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw GatewayException.invalid("REQUIRED", "venue", "Required");
        }
        if (value.length() > 30) {
            throw GatewayException.invalid("TOO_LONG", "venue", "Up to 30 characters");
        }
        return value;
    }

    public static void checkRow(int index, String prefix, String rawName, Long price, Integer total) {
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

    public static String slug(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "event" : s;
    }

    public static String shortName(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.endsWith(" BANGKOK") ? upper.substring(0, upper.length() - " BANGKOK".length()) : upper;
    }
}
