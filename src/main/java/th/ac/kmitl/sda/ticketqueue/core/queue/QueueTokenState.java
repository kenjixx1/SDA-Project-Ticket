package th.ac.kmitl.sda.ticketqueue.core.queue;

/** The states of a queue token. */
public enum QueueTokenState {
    /** In line. Position is shown and refreshed. */
    WAITING,
    /** Through the door. May now select and hold. */
    ADMITTED,
    /** Used for a reservation. Cannot be used again. */
    SPENT,
    /** Abandoned after admission, or its reservation ran out of time. */
    EXPIRED
}
