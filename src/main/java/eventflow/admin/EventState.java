package eventflow.admin;

public enum EventState {
    SCHEDULED,
    ON_SALE,
    CLOSING,   // closed to new fans, but some seats are still inside someone's 10 minutes
    CLOSED
}
