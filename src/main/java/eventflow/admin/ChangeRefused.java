package eventflow.admin;

// An organizer change that is not allowed in the event's current state.
public final class ChangeRefused extends RuntimeException {

    public enum Reason {
        EVENT_LOCKED,       // closing or closed: nothing can change
        SALE_TIME_LOCKED,   // the sale is on, so its start time is history
        PRICE_FROZEN,       // the sale is on, so no customer sees a price change mid-purchase
        BELOW_HELD_SOLD,    // fewer seats than are already held or sold
        NOT_ON_SALE         // only a sale that is on can be closed
    }

    private final Reason reason;
    private final int held;
    private final int sold;

    ChangeRefused(Reason reason, String message) {
        this(reason, message, 0, 0);
    }

    ChangeRefused(Reason reason, String message, int held, int sold) {
        super(message);
        this.reason = reason;
        this.held = held;
        this.sold = sold;
    }

    public Reason reason() { return reason; }
    public int held()      { return held; }
    public int sold()      { return sold; }
}
