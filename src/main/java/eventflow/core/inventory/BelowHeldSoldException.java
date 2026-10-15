package eventflow.core.inventory;

// A tier cannot shrink below the seats already held or sold.
public final class BelowHeldSoldException extends IllegalStateException {

    private final int held;
    private final int sold;

    BelowHeldSoldException(int held, int sold) {
        super("Cannot reduce below " + (held + sold) + ": " + sold + " sold and " + held + " held");
        this.held = held;
        this.sold = sold;
    }

    public int held() { return held; }
    public int sold() { return sold; }
}
