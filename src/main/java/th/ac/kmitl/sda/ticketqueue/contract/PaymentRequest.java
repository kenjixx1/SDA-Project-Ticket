package th.ac.kmitl.sda.ticketqueue.contract;

// No card numbers here. The provider handles those.
public record PaymentRequest(String reservationId, String idempotencyKey, Money amount) {
}
