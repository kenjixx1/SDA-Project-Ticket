package eventflow.core.order;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HoldExpiryScheduler {

    private final ReservationStore store;
    private final ReservationService reservations;
    private ScheduledExecutorService timer;

    public HoldExpiryScheduler(ReservationStore store, ReservationService reservations) {
        this.store = store;
        this.reservations = reservations;
    }

    // Looks through saved holds instead of trusting timers, so none are missed.
    public int sweep() {
        int expired = 0;
        for (Reservation reservation : store.inState(ReservationState.HELD)) {
            if (reservations.expireIfDue(reservation)) {
                expired++;
            }
        }
        return expired;
    }

    public synchronized void start() {
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(this::sweep, 1, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized void stop() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }
}
