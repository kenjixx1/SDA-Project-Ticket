package th.ac.kmitl.sda.ticketqueue.core.queue;

/** The answer to "let me into the sale": a token and a place, or why not. */
public record JoinResult(Outcome outcome, QueueToken token) {

    public enum Outcome {
        /** A new token at the back of the line. */
        JOINED,
        /** Already in line: the same token, the same place. */
        ALREADY_IN_QUEUE,
        /** The sale has not opened yet. */
        SALE_NOT_OPEN,
        /** Every tier is sold out. */
        SOLD_OUT
    }

    public boolean hasToken() {
        return token != null;
    }

    static JoinResult refused(Outcome outcome) {
        return new JoinResult(outcome, null);
    }
}
