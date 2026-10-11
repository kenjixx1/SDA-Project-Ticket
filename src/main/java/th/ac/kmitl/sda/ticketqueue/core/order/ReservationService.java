package th.ac.kmitl.sda.ticketqueue.core.order;

import java.time.Clock;

import th.ac.kmitl.sda.ticketqueue.contract.PaymentProvider;
import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.event.HoldExpired;
import th.ac.kmitl.sda.ticketqueue.core.event.OrderConfirmed;
import th.ac.kmitl.sda.ticketqueue.core.event.PaymentCompleted;
import th.ac.kmitl.sda.ticketqueue.core.event.PaymentVoided;
import th.ac.kmitl.sda.ticketqueue.core.registry.PluginRegistry;

/**
 * Owns the reservation lifecycle: HELD → CONFIRMED / EXPIRED.
 *
 * <p>Listens to {@link PaymentCompleted}. Publishes {@link OrderConfirmed},
 * {@link HoldExpired} and {@link PaymentVoided}.
 */
public final class ReservationService {

    private final ReservationStore store;
    private final EventBus bus;
    private final PluginRegistry registry;
    private final Clock clock;

    public ReservationService(ReservationStore store, EventBus bus,
                              PluginRegistry registry, Clock clock) {
        this.store = store;
        this.bus = bus;
        this.registry = registry;
        this.clock = clock;
        bus.subscribe(PaymentCompleted.class, this::onPaymentCompleted);
    }

    /**
     * Expires the hold if its ten minutes are up and nothing confirmed it first.
     * Returns whether this call did the expiring.
     */
    public boolean expireIfDue(Reservation reservation) {
        if (!reservation.isDue(clock.instant())) {
            return false;
        }
        if (!reservation.moveState(ReservationState.HELD, ReservationState.EXPIRED)) {
            return false;  // already confirmed or already expired
        }
        bus.publish(new HoldExpired(reservation.reservationId(), reservation.tokenId(),
                                    reservation.tierId(), reservation.quantity()));
        return true;
    }

    /** Confirm only while still HELD and before expiresAt. Otherwise void the payment. */
    private void onPaymentCompleted(PaymentCompleted payment) {
        Reservation reservation = store.find(payment.reservationId()).orElseThrow();
        PaymentProvider provider = registry.payment(payment.providerKey());

        boolean stillAlive = !reservation.isDue(clock.instant());
        if (stillAlive && reservation.moveState(ReservationState.HELD, ReservationState.CONFIRMED)) {
            provider.capture(payment.providerReference());
            bus.publish(new OrderConfirmed(reservation.reservationId(), reservation.tokenId(),
                    reservation.tierId(), reservation.quantity(), reservation.totalAmount()));
            return;
        }
        if (reservation.state() == ReservationState.CONFIRMED) {
            return;  // the same payment arrived twice; it was already counted
        }
        expireIfDue(reservation);  // the hold ran out while the bank was answering
        provider.voidAuthorisation(payment.providerReference());
        bus.publish(new PaymentVoided(reservation.reservationId(),
                payment.providerKey(), payment.providerReference()));
    }
}
