package th.ac.kmitl.sda.ticketqueue.core.queue;

/** Whether a token may be used for a hold right now. */
public enum ClaimResult {
    /** Admitted, for this event, and now spent on this hold. */
    CLAIMED,
    /** Still waiting, expired, unknown, or for another event. */
    NOT_ADMITTED,
    /** Already used for a hold. One token, one hold. */
    ALREADY_USED
}
