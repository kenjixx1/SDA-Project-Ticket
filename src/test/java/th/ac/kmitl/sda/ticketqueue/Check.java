package th.ac.kmitl.sda.ticketqueue;

import java.util.Objects;

import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.Reservation;
import th.ac.kmitl.sda.ticketqueue.core.order.ReservationState;

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

    // Never oversold, and the counts match the reservations.
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
                "oversold: " + tier);
        equal(held, tier.heldQuantity(), "heldQuantity vs HELD reservations");
        equal(sold, tier.soldQuantity(), "soldQuantity vs CONFIRMED reservations");
    }
}
