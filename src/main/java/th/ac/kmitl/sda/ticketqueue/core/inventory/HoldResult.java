package th.ac.kmitl.sda.ticketqueue.core.inventory;

import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;

public record HoldResult(Outcome outcome, Reservation reservation) {

    public enum Outcome {
        HELD,
        NOT_ENOUGH_SEATS,
        OVER_ORDER_LIMIT,
        NOT_ADMITTED,  // still waiting, expired or unknown
        TOKEN_ALREADY_USED
    }

    public boolean held() {
        return outcome == Outcome.HELD;
    }

    static HoldResult refused(Outcome outcome) {
        return new HoldResult(outcome, null);
    }
}
