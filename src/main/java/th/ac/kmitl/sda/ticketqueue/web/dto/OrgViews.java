package th.ac.kmitl.sda.ticketqueue.web.dto;

import java.util.List;

/** Every reply shape the organizer page receives. Field names are the JSON names. */
public final class OrgViews {

    private OrgViews() {
    }

    /** O-01 row. state: DRAFT, SCHEDULED, ON_SALE, CLOSING, CLOSED, CANCELLED. */
    public record OrgEventRow(String eventId, String name, String venue, long eventDateTime, long saleOpensAt,
                              String state, int sold, int total) {
    }

    /** held + sold + remaining == total, always. */
    public record OrgTier(String tierId, String name, long price, int total, int held, int sold, int remaining) {
    }

    /** O-02 to O-10. waiting: people in the queue now (0 unless ON_SALE). closedAt: null until CLOSED. */
    public record OrgEvent(String eventId, String name, String venue, long eventDateTime, long saleOpensAt,
                           String state, int admitPerSecond, List<OrgTier> tiers, int heldNow, int waiting,
                           Long closedAt) {
    }

    /** O-11. all: the sum row, tierId "all", name "All", price 0. revenue: confirmed orders only, in baht. */
    public record Dashboard(String eventId, String name, String state, int waiting, int admitPerSecond,
                            List<OrgTier> tiers, OrgTier all, long revenue, Long closedAt) {
    }
}
