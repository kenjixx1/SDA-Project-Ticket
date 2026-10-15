package eventflow.core.event;

// The user gave the seats back before the 10 minutes ended.
public record HoldReleased(String reservationId, String tokenId, String tierId,
                           int quantity) implements DomainEvent {
}
