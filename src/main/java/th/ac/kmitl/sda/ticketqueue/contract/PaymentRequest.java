package th.ac.kmitl.sda.ticketqueue.contract;

/** What the core sends a payment provider. No card number, ever. */
public record PaymentRequest(String reservationId, String idempotencyKey, Money amount) {
}
