package th.ac.kmitl.sda.ticketqueue.core.inventory;

import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;

/** The answer to "hold these seats": a reservation, or the reason why not. */
public record HoldResult(Outcome outcome, Reservation reservation) {

    public enum Outcome {
        HELD,
        /** More than the tier has left. Nothing is held (no partial holds). */
        NOT_ENOUGH_SEATS,
        /** More than 4 tickets in one order. */
        OVER_ORDER_LIMIT,
        /** The queue token is not admitted (still waiting, expired, or unknown). */
        NOT_ADMITTED,
        /** One queue token buys once. */
        TOKEN_ALREADY_USED
    }

    public boolean held() {
        return outcome == Outcome.HELD;
    }

    static HoldResult refused(Outcome outcome) {
        return new HoldResult(outcome, null);
    }
}
