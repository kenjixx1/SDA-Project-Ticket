package th.ac.kmitl.sda.ticketqueue.core.order;

/** What Kenji is told after pressing Pay. */
public enum CheckoutResult {
    /** Paid and the seats are his. */
    CONFIRMED,
    /** The provider refused. The hold keeps its remaining time (FR-19). */
    DECLINED,
    /** The ten minutes ran out. Not charged. */
    HOLD_EXPIRED
}
