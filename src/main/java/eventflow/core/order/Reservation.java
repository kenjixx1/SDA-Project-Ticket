package eventflow.core.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import eventflow.contract.Money;
import eventflow.contract.Promotion;

public final class Reservation {

    public static final Duration HOLD_TIME = Duration.ofMinutes(10);

    private final String reservationId;
    private final String tokenId;
    private final String tierId;
    private final int quantity;
    private final Money unitPrice;
    private final List<Promotion> promotions;
    private final Money totalAmount;
    private final Instant heldAt;
    private final Instant expiresAt;
    private final AtomicReference<ReservationState> state =
            new AtomicReference<>(ReservationState.HELD);

    public Reservation(String reservationId, String tokenId, String tierId,
                       int quantity, Money unitPrice, Instant heldAt) {
        this(reservationId, tokenId, tierId, quantity, unitPrice, List.of(), unitPrice.times(quantity), heldAt);
    }

    public Reservation(String reservationId, String tokenId, String tierId, int quantity, Money unitPrice,
                       List<Promotion> promotions, Money totalAmount, Instant heldAt) {
        this.reservationId = reservationId;
        this.tokenId = tokenId;
        this.tierId = tierId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.promotions = List.copyOf(promotions);
        this.totalAmount = totalAmount;
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
    public List<Promotion> promotions() { return promotions; }
    public Money totalAmount()    { return totalAmount; }
    public Instant heldAt()       { return heldAt; }
    public Instant expiresAt()    { return expiresAt; }
    public ReservationState state() { return state.get(); }

    @Override
    public String toString() {
        return reservationId + " " + quantity + " × " + tierId + " " + state.get();
    }
}
