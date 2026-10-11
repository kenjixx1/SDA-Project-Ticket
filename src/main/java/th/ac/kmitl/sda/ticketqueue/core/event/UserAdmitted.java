package th.ac.kmitl.sda.ticketqueue.core.event;

/** The waiting room let this customer through. Published by {@code AdmissionScheduler}. */
public record UserAdmitted(String tokenId, String eventId, String customerId) implements DomainEvent {
}
