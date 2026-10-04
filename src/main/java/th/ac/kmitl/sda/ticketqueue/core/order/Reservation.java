package th.ac.kmitl.sda.ticketqueue.core.order;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import th.ac.kmitl.sda.ticketqueue.contract.Money;

/**
 * E-04 · Seats taken out of the pool for one customer, with a deadline.
 *
 * <p>Everything is fixed at hold time except the state. The state changes by
 * compare-and-set, so when two things race for the same hold — payment and
 * expiry — exactly one of them wins (R-E04-6).
 */
public final class Reservation {

    /** Locked decision 2: the hold time is the server's, not the customer's. */
    public static final Duration HOLD_TIME = Duration.ofMinutes(10);

    private final String reservationId;
    private final String tokenId;
    private final String tierId;
    private final int quantity;
    private final Money unitPrice;
    private final Money totalAmount;
    private final Instant heldAt;
    private final Instant expiresAt;
    private final AtomicReference<ReservationState> state =
            new AtomicReference<>(ReservationState.HELD);

    public Reservation(String reservationId, String tokenId, String tierId,
                       int quantity, Money unitPrice, Instant heldAt) {
        this.reservationId = reservationId;
        this.tokenId = tokenId;
        this.tierId = tierId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = unitPrice.times(quantity);
        this.heldAt = heldAt;
        this.expiresAt = heldAt.plus(HOLD_TIME);  // R-E04-3
    }

    /** Moves the state only if it is still {@code from}. Returns whether this call won. */
    boolean moveState(ReservationState from, ReservationState to) {
        return state.compareAndSet(from, to);
    }

    public boolean isDue(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Derived from the reservation, so a repeated checkout is the same payment (FR-18). */
    public String idempotencyKey() {
        return "pay-" + reservationId;
    }

    public String reservationId() { return reservationId; }
    public String tokenId()       { return tokenId; }
    public String tierId()        { return tierId; }
    public int quantity()         { return quantity; }
    public Money unitPrice()      { return unitPrice; }
    public Money totalAmount()    { return totalAmount; }
    public Instant heldAt()       { return heldAt; }
    public Instant expiresAt()    { return expiresAt; }
    public ReservationState state() { return state.get(); }

    @Override
    public String toString() {
        return reservationId + " " + quantity + " × " + tierId + " " + state.get();
    }
}
