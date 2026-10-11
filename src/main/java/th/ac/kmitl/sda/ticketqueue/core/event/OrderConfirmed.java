package th.ac.kmitl.sda.ticketqueue.core.event;

import th.ac.kmitl.sda.ticketqueue.contract.Money;

public record OrderConfirmed(String reservationId, String tokenId, String tierId,
                             int quantity, Money totalAmount) implements DomainEvent {
}
