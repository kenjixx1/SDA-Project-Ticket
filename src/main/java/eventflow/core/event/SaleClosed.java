package eventflow.core.event;

// The organizer closed the sale: nobody new is let in. Holds already running keep their time.
public record SaleClosed(String eventId) implements DomainEvent {
}
