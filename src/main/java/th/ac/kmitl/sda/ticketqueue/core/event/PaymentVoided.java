package th.ac.kmitl.sda.ticketqueue.core.event;

/** Payment arrived after the hold expired, so it was cancelled. Kenji is not charged. */
public record PaymentVoided(String reservationId, String providerKey,
                            String providerReference) implements DomainEvent {
}
