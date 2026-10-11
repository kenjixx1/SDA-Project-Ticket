package eventflow.core.event;

import eventflow.contract.Money;

public record PaymentCompleted(String reservationId, String providerKey,
                               String providerReference, Money amount) implements DomainEvent {
}
