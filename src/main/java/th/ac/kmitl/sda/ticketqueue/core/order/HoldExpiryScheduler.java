package th.ac.kmitl.sda.ticketqueue.core.order;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * UC-02 extension 6a · gives the seats back when ten minutes pass (FR-12).
 *
 * <p>It scans the store for HELD reservations past their {@code expiresAt}
 * rather than trusting a timer per hold. The store is the truth, so a lost
 * event or a restart cannot leave seats stuck.
 */
public final class HoldExpiryScheduler {

    private final ReservationStore store;
    private final ReservationService reservations;
    private ScheduledExecutorService timer;

    public HoldExpiryScheduler(ReservationStore store, ReservationService reservations) {
        this.store = store;
        this.reservations = reservations;
    }

    /** One pass. Returns how many holds it expired. */
    public int sweep() {
        int expired = 0;
        for (Reservation reservation : store.inState(ReservationState.HELD)) {
            if (reservations.expireIfDue(reservation)) {
                expired++;
            }
        }
        return expired;
    }

    /** Sweeps once a second in the background. */
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
