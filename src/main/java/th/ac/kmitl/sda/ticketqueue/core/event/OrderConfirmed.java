package th.ac.kmitl.sda.ticketqueue.core.event;

import th.ac.kmitl.sda.ticketqueue.contract.Money;

/** Payment arrived while the hold was still alive. The seats are sold (FR-16). */
public record OrderConfirmed(String reservationId, String tokenId, String tierId,
                             int quantity, Money totalAmount) implements DomainEvent {
}
