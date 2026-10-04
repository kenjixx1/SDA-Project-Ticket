package th.ac.kmitl.sda.ticketqueue.core.order;

/** E-04 states. Only {@code HELD} can move, and only once. */
public enum ReservationState {
    HELD,
    CONFIRMED,
    EXPIRED,
    RELEASED
}
