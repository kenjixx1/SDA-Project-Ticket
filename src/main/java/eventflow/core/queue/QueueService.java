package eventflow.core.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import eventflow.core.event.EventBus;
import eventflow.core.event.HoldExpired;

public final class QueueService {

    public static final int DEFAULT_ADMISSION_RATE = 200;

    private final Map<String, Line> lines = new ConcurrentHashMap<>();
    private final Map<String, QueueToken> tokens = new ConcurrentHashMap<>();
    private final Map<String, QueueToken> activeByCustomer = new ConcurrentHashMap<>();
    private final AtomicLong nextToken = new AtomicLong(1);
    private final Predicate<String> soldOut;
    private final Clock clock;

    static final class Line {
        final Queue<QueueToken> waiting = new ConcurrentLinkedQueue<>();
        volatile boolean open;
        volatile int admissionRate = DEFAULT_ADMISSION_RATE;
        volatile long admittedThrough;
        long nextSequence = 1;
    }

    public QueueService(EventBus bus, Clock clock, Predicate<String> soldOut) {
        this.clock = clock;
        this.soldOut = soldOut;
        bus.subscribe(HoldExpired.class, this::onHoldExpired);
    }

    public void openSale(String eventId) {
        line(eventId).open = true;
    }

    public void setAdmissionRate(String eventId, int perSecond) {
        if (perSecond < 1) {
            throw new IllegalArgumentException("Admission rate must be at least 1 per second");
        }
        line(eventId).admissionRate = perSecond;
    }

    public int admissionRate(String eventId) {
        return line(eventId).admissionRate;
    }

    public JoinResult join(String customerId, String eventId) {
        Line line = line(eventId);
        if (!line.open) {
            return JoinResult.refused(JoinResult.Outcome.SALE_NOT_OPEN);
        }
        String key = customerId + "@" + eventId;
        // Ten tabs, one token: give back the token they already have.
        QueueToken existing = activeByCustomer.get(key);
        if (existing != null && existing.isActive()) {
            return new JoinResult(JoinResult.Outcome.ALREADY_IN_QUEUE, existing);
        }
        if (soldOut.test(eventId)) {
            return JoinResult.refused(JoinResult.Outcome.SOLD_OUT);
        }
        synchronized (line) {
            existing = activeByCustomer.get(key);  // another tab may have joined meanwhile
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

    public QueueStatus status(String tokenId) {
        QueueToken token = token(tokenId);
        if (token.state() != QueueTokenState.WAITING) {
            return new QueueStatus(token.state(), 0, Duration.ZERO);
        }
        Line line = line(token.eventId());
        // Fans are let in strictly in order, so place = my number minus the last one let in.
        long position = Math.max(1, token.sequence() - line.admittedThrough);
        long seconds = (position + line.admissionRate - 1) / line.admissionRate;
        return new QueueStatus(QueueTokenState.WAITING, position, Duration.ofSeconds(seconds));
    }

    public int queueLength(String eventId) {
        return line(eventId).waiting.size();
    }

    // Only an admitted token can hold, and only once.
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
