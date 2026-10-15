package eventflow.core.event;

import java.util.List;

public record TicketIssued(String reservationId, String tokenId, List<String> ticketIds) implements DomainEvent {
}
