package th.ac.kmitl.sda.ticketqueue.web.dto;

import java.util.List;

/** Every reply shape the page receives. Field names are the JSON names. */
public final class Views {

    private Views() {
    }

    /** M-01. state: SCHEDULED, ON_SALE, CLOSED. */
    public record EventSummary(String eventId, String name, String shortName, String venue,
                               long eventDateTime, long saleOpensAt, String state,
                               long fromPrice, String artwork, boolean soldOut) {
    }

    public record TierPrice(String tierId, String name, long price) {
    }

    /** M-02. state: SCHEDULED, ON_SALE, CLOSED, CANCELLED. myToken is null when the customer is not in line. */
    public record EventDetail(String eventId, String name, String shortName, String venue,
                              long eventDateTime, long saleOpensAt, String state, String artwork,
                              boolean soldOut, List<TierPrice> tiers, String myToken, Integer myPosition) {
    }

    /** M-03. isExisting true means the customer already had a place (SCR-06c). */
    public record JoinResult(String token, String eventId, int position, boolean isExisting) {
    }

    /** M-04. state: WAITING, ADMITTED, SPENT, EXPIRED. */
    public record QueueStatus(String token, String eventId, String state, int position,
                              int moved, int admitPerSecond) {
    }

    /** M-05. */
    public record TierAvailability(String tierId, String name, long price, int remaining, int total) {
    }

    public record EventRef(String eventId, String name, String shortName, String venue, long eventDateTime) {
    }

    /** amount is the discount in baht, a positive number. */
    public record Promotion(String name, long amount) {
    }

    public record PaymentMethod(String key, String tile, String label, String line, boolean available) {
    }

    /** M-06, M-07, M-08. state: HELD, CONFIRMED, EXPIRED, RELEASED. paymentId is the latest payment, or null. */
    public record HoldView(String reservationId, String token, String state, EventRef event,
                           String tierId, String tierName, int quantity, long unitPrice,
                           List<Promotion> promotions, long totalAmount, long expiresAt,
                           int remainingBefore, int remainingAfter,
                           List<PaymentMethod> paymentMethods, String paymentId) {
    }

    /** M-09. display: what the provider wants shown (the QR text, or the simulated card page line). */
    public record PaymentStart(String paymentId, String reservationId, String providerKey,
                               String providerLabel, long amount, String display) {
    }

    /** M-10, M-11. state: PENDING, CAPTURED, DECLINED, VOIDED. answered: the customer pressed pay or a card button. */
    public record PaymentStatus(String paymentId, String reservationId, String providerKey,
                                String state, boolean answered, String orderId) {
    }

    /** state: VALID, CANCELLED. */
    public record TicketView(String ticketId, int n, int of, String payload, String state) {
    }

    /** M-12. eventState: the event's state now. past: the event date has passed. */
    public record OrderView(String orderId, EventRef event, String eventState, String tierId,
                            String tierName, int quantity, long paidAmount, String providerLabel,
                            boolean past, List<TicketView> tickets) {
    }

    /** M-13. */
    public record Me(String customerId, String name, String maskedEmail) {
    }
}
