package th.ac.kmitl.sda.ticketqueue.core.inventory;

import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;

/** The answer to "hold these seats": a reservation, or the reason why not. */
public record HoldResult(Outcome outcome, Reservation reservation) {

    public enum Outcome {
        HELD,
        /** FR-10 · more than the tier has left. Nothing is held (no partial holds). */
        NOT_ENOUGH_SEATS,
        /** FR-11 · more than 4 tickets in one order. */
        OVER_ORDER_LIMIT,
        /** Locked decision 3 · one queue token buys once. */
        TOKEN_ALREADY_USED
    }

    public boolean held() {
        return outcome == Outcome.HELD;
    }

    static HoldResult refused(Outcome outcome) {
        return new HoldResult(outcome, null);
    }
}
