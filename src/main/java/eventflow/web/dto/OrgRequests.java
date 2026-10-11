package eventflow.web.dto;

import java.util.List;

/** Every request body the organizer page sends. A null field means "not given". */
public final class OrgRequests {

    private OrgRequests() {
    }

    public record EventDetails(String name, String venue, Long eventDateTime, Long saleOpensAt, Integer admitPerSecond) {
    }

    public record TierInput(String name, Long price, Integer total) {
    }

    public record TierList(List<TierInput> tiers) {
    }

    public record RateInput(Integer admitPerSecond) {
    }
}
