package th.ac.kmitl.sda.ticketqueue.core.event;

import java.time.Instant;

/** The atomic hold succeeded. Published by {@code InventoryService}. */
public record TicketHeld(String reservationId, String tokenId, String tierId,
                         int quantity, Instant expiresAt) implements DomainEvent {
}
