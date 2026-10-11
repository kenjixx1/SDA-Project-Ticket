package eventflow.core.event;

public record PaymentVoided(String reservationId, String providerKey,
                            String providerReference) implements DomainEvent {
}
