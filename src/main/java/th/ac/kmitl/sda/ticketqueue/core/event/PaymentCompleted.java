package th.ac.kmitl.sda.ticketqueue.core.event;

import th.ac.kmitl.sda.ticketqueue.contract.Money;

/** A payment provider authorised the money. The order is not confirmed yet. */
public record PaymentCompleted(String reservationId, String providerKey,
                               String providerReference, Money amount) implements DomainEvent {
}
