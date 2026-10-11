package th.ac.kmitl.sda.ticketqueue.core.order;

/** The states of a reservation. Only {@code HELD} can move, and only once. */
public enum ReservationState {
    HELD,
    CONFIRMED,
    EXPIRED,
    RELEASED
}
