package eventflow.core.queue;

public enum QueueTokenState {
    WAITING,

    ADMITTED,

    SPENT,

    EXPIRED
}
