package th.ac.kmitl.sda.ticketqueue.core.event;

import th.ac.kmitl.sda.ticketqueue.contract.Money;

public record PaymentCompleted(String reservationId, String providerKey,
                               String providerReference, Money amount) implements DomainEvent {
}
