package eventflow.core.event;

import eventflow.contract.Money;

public record OrderConfirmed(String reservationId, String tokenId, String tierId,
                             int quantity, Money totalAmount) implements DomainEvent {
}
