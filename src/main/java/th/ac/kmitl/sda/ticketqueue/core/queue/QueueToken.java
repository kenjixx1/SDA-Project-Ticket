package th.ac.kmitl.sda.ticketqueue.core.queue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A customer's numbered place in the waiting room.
 *
 * <p>Also the identity for the whole purchase: the token
 * that was admitted is the token that holds, and it can hold only once.
 */
public final class QueueToken {

    private final String tokenId;
    private final String eventId;
    private final String customerId;
    private final long sequence;
    private final Instant issuedAt;
    private final AtomicReference<QueueTokenState> state =
            new AtomicReference<>(QueueTokenState.WAITING);
    private volatile Instant admittedAt;

    QueueToken(String tokenId, String eventId, String customerId, long sequence, Instant issuedAt) {
        this.tokenId = tokenId;
        this.eventId = eventId;
        this.customerId = customerId;
        this.sequence = sequence;
        this.issuedAt = issuedAt;
    }

    /** Moves the state only if it is still {@code from}. Returns whether this call won. */
    boolean moveState(QueueTokenState from, QueueTokenState to) {
        return state.compareAndSet(from, to);
    }

    void markAdmitted(Instant now) {
        admittedAt = now;
        state.set(QueueTokenState.ADMITTED);
    }

    boolean isActive() {
        QueueTokenState s = state.get();
        return s == QueueTokenState.WAITING || s == QueueTokenState.ADMITTED;
    }

    public String tokenId()         { return tokenId; }
    public String eventId()         { return eventId; }
    public String customerId()      { return customerId; }
    /** 1 for the first person in line, 2 for the next. Sets the place in line. */
    public long sequence()          { return sequence; }
    public Instant issuedAt()       { return issuedAt; }
    public Instant admittedAt()     { return admittedAt; }
    public QueueTokenState state()  { return state.get(); }

    @Override
    public String toString() {
        return tokenId + " #" + sequence + " " + customerId + " " + state.get();
    }
}
