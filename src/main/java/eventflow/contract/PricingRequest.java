package eventflow.contract;

import java.time.Instant;

// What a pricing rule may look at. Small on purpose.
public record PricingRequest(String eventId, String tierId, int quantity, String customerId,
                             Money unitPrice, Instant heldAt, Instant saleOpenedAt) {
}
