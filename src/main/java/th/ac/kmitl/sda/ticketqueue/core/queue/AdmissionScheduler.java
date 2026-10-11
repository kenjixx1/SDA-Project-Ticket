package th.ac.kmitl.sda.ticketqueue.core.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.event.UserAdmitted;

/**
 * Lets the next batch in, every second, strictly first in first out. The
 * batch size is the organizer's admission rate.
 *
 * <p>Publishes {@link UserAdmitted} for each customer let in. Also expires
 * admitted tokens that never placed a hold.
 */
public final class AdmissionScheduler {

    /** An admitted customer has this long to place a hold. */
    public static final Duration ADMISSION_WINDOW = Duration.ofMinutes(5);

    private final QueueService queue;
    private final EventBus bus;
    private final Clock clock;
    private ScheduledExecutorService timer;

    public AdmissionScheduler(QueueService queue, EventBus bus, Clock clock) {
        this.queue = queue;
        this.bus = bus;
        this.clock = clock;
    }

    /** Admits one batch for one event. Returns how many were let in. */
    public int admitBatch(String eventId) {
        QueueService.Line line = queue.line(eventId);
        int admitted = 0;
        synchronized (line) {  // one batch at a time keeps the order exact
            while (admitted < line.admissionRate) {
                QueueToken next = line.waiting.poll();
                if (next == null) {
                    break;
                }
                next.markAdmitted(clock.instant());
                line.admittedThrough = next.sequence();
                admitted++;
                bus.publish(new UserAdmitted(next.tokenId(), next.eventId(), next.customerId()));
            }
        }
        return admitted;
    }

    /** Admitted but did nothing. Returns how many tokens expired. */
    public int expireIdle() {
        int expired = 0;
        for (QueueToken token : queue.allTokens()) {
            if (token.state() == QueueTokenState.ADMITTED
                    && !clock.instant().isBefore(token.admittedAt().plus(ADMISSION_WINDOW))
                    && token.moveState(QueueTokenState.ADMITTED, QueueTokenState.EXPIRED)) {
                expired++;
            }
        }
        return expired;
    }

    /** Runs admission for one event once a second in the background. */
    public synchronized void start(String eventId) {
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor();
        }
        timer.scheduleAtFixedRate(() -> {
            admitBatch(eventId);
            expireIdle();
        }, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }
}
