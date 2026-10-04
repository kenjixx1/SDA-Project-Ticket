package th.ac.kmitl.sda.ticketqueue;

import java.util.Objects;

import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationState;

/** Assertions, plus the domain invariants every test can re-check. */
public final class Check {

    private Check() {
    }

    public static void that(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void equal(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    /** INV-01, INV-02 and INV-03 for one tier. */
    public static void invariants(EventFlow app, TicketTier tier) {
        int held = 0;
        int sold = 0;
        for (Reservation r : app.store().all()) {
            if (!r.tierId().equals(tier.tierId())) {
                continue;
            }
            if (r.state() == ReservationState.HELD) {
                held += r.quantity();
            } else if (r.state() == ReservationState.CONFIRMED) {
                sold += r.quantity();
            }
        }
        that(tier.heldQuantity() + tier.soldQuantity() <= tier.totalQuantity(),
                "INV-01 broken, oversold: " + tier);
        equal(held, tier.heldQuantity(), "INV-02 heldQuantity vs HELD reservations");
        equal(sold, tier.soldQuantity(), "INV-03 soldQuantity vs CONFIRMED reservations");
    }
}
