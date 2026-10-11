package eventflow.contract;

// No card numbers here. The provider handles those.
public record PaymentRequest(String reservationId, String idempotencyKey, Money amount) {
}
