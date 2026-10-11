package th.ac.kmitl.sda.ticketqueue.core.event;

public record PaymentVoided(String reservationId, String providerKey,
                            String providerReference) implements DomainEvent {
}
