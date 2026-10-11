package th.ac.kmitl.sda.ticketqueue.core.event;

public record UserAdmitted(String tokenId, String eventId, String customerId) implements DomainEvent {
}
