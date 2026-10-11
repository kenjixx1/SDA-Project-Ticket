package th.ac.kmitl.sda.ticketqueue.core.event;

public record HoldExpired(String reservationId, String tokenId, String tierId,
                          int quantity) implements DomainEvent {
}
