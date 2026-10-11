package th.ac.kmitl.sda.ticketqueue.core.event;

import java.time.Instant;

public record TicketHeld(String reservationId, String tokenId, String tierId,
                         int quantity, Instant expiresAt) implements DomainEvent {
}
