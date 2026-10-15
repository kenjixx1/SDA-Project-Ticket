package eventflow.core.queue;

public enum ClaimResult {
    CLAIMED,
    NOT_ADMITTED,
    ALREADY_USED,
    SALE_CLOSED
}
