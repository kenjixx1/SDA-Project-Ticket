package th.ac.kmitl.sda.ticketqueue.core.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.event.HoldExpired;

/**
 * The virtual waiting room. Issues tokens and reports places.
 *
 * <p>It never lets anyone in by itself — {@link AdmissionScheduler} does that,
 * in batches, at the organizer's rate. Listens to {@link HoldExpired}: a token
 * whose hold ran out is expired, so the customer must rejoin.
 */
public final class QueueService {

    /** The default admission rate, per second. */
    public static final int DEFAULT_ADMISSION_RATE = 200;

    private final Map<String, Line> lines = new ConcurrentHashMap<>();
    private final Map<String, QueueToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, QueueToken> activeByCustomer = new ConcurrentHashMap<>();
    private final AtomicLong nextToken = new AtomicLong(1);
    private final Predicate<String> soldOut;
    private final Clock clock;

    /** One event's line. Sequence numbers are issued under its lock, so line order = issue order. */
    static final class Line {
        final Queue<QueueToken> waiting = new ConcurrentLinkedQueue<>();
        volatile boolean open;
        volatile int admissionRate = DEFAULT_ADMISSION_RATE;
        volatile long admittedThrough;   // sequence of the last token let in
        long nextSequence = 1;
    }

    /**
     * @param soldOut answers "is every tier of this event sold out?"
     *                Passed in so the queue does not depend on inventory's class.
     */
    public QueueService(EventBus bus, Clock clock, Predicate<String> soldOut) {
        this.clock = clock;
        this.soldOut = soldOut;
        bus.subscribe(HoldExpired.class, this::onHoldExpired);
    }

    public void openSale(String eventId) {
        line(eventId).open = true;
    }

    /** The organizer sets how many customers are let in each second. */
    public void setAdmissionRate(String eventId, int perSecond) {
        if (perSecond < 1) {
            throw new IllegalArgumentException("Admission rate must be at least 1 per second");
        }
        line(eventId).admissionRate = perSecond;
    }

    public int admissionRate(String eventId) {
        return line(eventId).admissionRate;
    }

    /**
     * Joins the line. One active token per customer per event: ten tabs get
     * the same token, and coming back gets the same place.
     */
    public JoinResult join(String customerId, String eventId) {
        Line line = line(eventId);
        if (!line.open) {
            return JoinResult.refused(JoinResult.Outcome.SALE_NOT_OPEN);
        }
        String key = customerId + "@" + eventId;
        QueueToken existing = activeByCustomer.get(key);
        if (existing != null && existing.isActive()) {
            return new JoinResult(JoinResult.Outcome.ALREADY_IN_QUEUE, existing);
        }
        if (soldOut.test(eventId)) {
            return JoinResult.refused(JoinResult.Outcome.SOLD_OUT);
        }
        synchronized (line) {
            existing = activeByCustomer.get(key);  // another tab may have won meanwhile
            if (existing != null && existing.isActive()) {
                return new JoinResult(JoinResult.Outcome.ALREADY_IN_QUEUE, existing);
            }
            QueueToken token = new QueueToken("Q-" + nextToken.getAndIncrement(), eventId,
                    customerId, line.nextSequence++, clock.instant());
            tokens.put(token.tokenId(), token);
            activeByCustomer.put(key, token);
            line.waiting.add(token);
            return new JoinResult(JoinResult.Outcome.JOINED, token);
        }
    }

    /**
     * Kenji's place and wait. Admission is strictly in order, so his
     * place is simply how far his number is past the last one let in.
     */
    public QueueStatus status(String tokenId) {
        QueueToken token = token(tokenId);
        if (token.state() != QueueTokenState.WAITING) {
            return new QueueStatus(token.state(), 0, Duration.ZERO);
        }
        Line line = line(token.eventId());
        long position = Math.max(1, token.sequence() - line.admittedThrough);
        long seconds = (position + line.admissionRate - 1) / line.admissionRate;  // one batch per second
        return new QueueStatus(QueueTokenState.WAITING, position, Duration.ofSeconds(seconds));
    }

    /** How many are still waiting. */
    public int queueLength(String eventId) {
        return line(eventId).waiting.size();
    }

    /**
     * Only an admitted token may hold, and only once.
     * Called by inventory inside its atomic hold.
     */
    public ClaimResult claimForHold(String tokenId, String eventId) {
        QueueToken token = tokens.get(tokenId);
        if (token == null || !token.eventId().equals(eventId)) {
            return ClaimResult.NOT_ADMITTED;
        }
        if (token.moveState(QueueTokenState.ADMITTED, QueueTokenState.SPENT)) {
            return ClaimResult.CLAIMED;
        }
        return token.state() == QueueTokenState.SPENT
                ? ClaimResult.ALREADY_USED
                : ClaimResult.NOT_ADMITTED;
    }

    public QueueToken token(String tokenId) {
        QueueToken token = tokens.get(tokenId);
        if (token == null) {
            throw new IllegalArgumentException("No queue token " + tokenId);
        }
        return token;
    }

    List<QueueToken> allTokens() {
        return List.copyOf(tokens.values());
    }

    Line line(String eventId) {
        return lines.computeIfAbsent(eventId, id -> new Line());
    }

    private void onHoldExpired(HoldExpired event) {
        QueueToken token = tokens.get(event.tokenId());
        if (token != null) {
            token.moveState(QueueTokenState.SPENT, QueueTokenState.EXPIRED);
        }
    }
}
