package th.ac.kmitl.sda.ticketqueue.core.event;

/** Ten minutes passed with no confirmation. The seats must go back on sale. */
public record HoldExpired(String reservationId, String tokenId, String tierId,
                          int quantity) implements DomainEvent {
}
