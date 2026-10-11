package th.ac.kmitl.sda.ticketqueue.core.event;

/**
 * Something that has already happened. News, not a request.
 *
 * <p>An event is published only after the change it describes has been saved.
 * Every event carries the queue token or the reservation it is about, so the
 * chain can be traced afterwards.
 */
public interface DomainEvent {
}
