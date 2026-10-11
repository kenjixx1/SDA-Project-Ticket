package eventflow.web.dto;

import java.util.List;

/** Every request body the organizer page sends. A null field means "not given". */
public final class OrgRequests {

    private OrgRequests() {
    }

    /** artwork: the event picture as a data URL (PNG, JPG or WebP, up to 2 MB). Null keeps it; "" removes it. */
    public record EventDetails(String name, String venue, Long eventDateTime, Long saleOpensAt, Integer admitPerSecond,
                               String artwork) {

        public EventDetails(String name, String venue, Long eventDateTime, Long saleOpensAt, Integer admitPerSecond) {
            this(name, venue, eventDateTime, saleOpensAt, admitPerSecond, null);
        }
    }

    public record TierInput(String name, Long price, Integer total) {
    }

    public record TierList(List<TierInput> tiers) {
    }

    public record RateInput(Integer admitPerSecond) {
    }
}
