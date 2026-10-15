package eventflow.core.queue;

public record JoinResult(Outcome outcome, QueueToken token) {

    public enum Outcome {
        JOINED,
        ALREADY_IN_QUEUE,
        SALE_NOT_OPEN,
        SALE_CLOSED,
        SOLD_OUT
    }

    public boolean hasToken() {
        return token != null;
    }

    static JoinResult refused(Outcome outcome) {
        return new JoinResult(outcome, null);
    }
}
