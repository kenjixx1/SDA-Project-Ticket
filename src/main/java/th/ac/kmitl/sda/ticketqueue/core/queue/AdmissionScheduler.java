package th.ac.kmitl.sda.ticketqueue.core.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.event.UserAdmitted;

public final class AdmissionScheduler {

    // Admitted fans have 5 minutes to hold seats, or they lose their place.
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

    public int admitBatch(String eventId) {
        QueueService.Line line = queue.line(eventId);
        int admitted = 0;
        synchronized (line) {  // one batch at a time keeps the line in order
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

    // Admitted but never held anything.
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
