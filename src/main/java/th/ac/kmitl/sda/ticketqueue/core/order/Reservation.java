package th.ac.kmitl.sda.ticketqueue.core.order;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import th.ac.kmitl.sda.ticketqueue.contract.Money;

public final class Reservation {

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
        this.expiresAt = heldAt.plus(HOLD_TIME);  // set by the server clock
    }

    // Only one change can win: paid or expired, never both.
    boolean moveState(ReservationState from, ReservationState to) {
        return state.compareAndSet(from, to);
    }

    public boolean isDue(Instant now) {
        return !now.isBefore(expiresAt);
    }

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
