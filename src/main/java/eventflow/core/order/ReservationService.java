package eventflow.core.order;

import java.time.Clock;

import eventflow.contract.PaymentProvider;
import eventflow.core.event.EventBus;
import eventflow.core.event.HoldExpired;
import eventflow.core.event.HoldReleased;
import eventflow.core.event.OrderConfirmed;
import eventflow.core.event.PaymentCompleted;
import eventflow.core.event.PaymentVoided;
import eventflow.core.registry.PluginRegistry;

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

    public boolean expireIfDue(Reservation reservation) {
        if (!reservation.isDue(clock.instant())) {
            return false;
        }
        if (!reservation.moveState(ReservationState.HELD, ReservationState.EXPIRED)) {
            return false;
        }
        bus.publish(new HoldExpired(reservation.reservationId(), reservation.tokenId(),
                                    reservation.tierId(), reservation.quantity()));
        return true;
    }

    // The user gives the seats back early. Returns false if the hold already ended.
    public boolean release(String reservationId) {
        Reservation reservation = store.find(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("No reservation " + reservationId));
        if (!reservation.moveState(ReservationState.HELD, ReservationState.RELEASED)) {
            return false;
        }
        bus.publish(new HoldReleased(reservation.reservationId(), reservation.tokenId(),
                                     reservation.tierId(), reservation.quantity()));
        return true;
    }

    private void onPaymentCompleted(PaymentCompleted payment) {
        Reservation reservation = store.find(payment.reservationId()).orElseThrow();
        PaymentProvider provider = registry.payment(payment.providerKey());

        // Confirm only if the hold is still alive. Otherwise cancel the payment.
        boolean stillAlive = !reservation.isDue(clock.instant());
        if (stillAlive && reservation.moveState(ReservationState.HELD, ReservationState.CONFIRMED)) {
            provider.capture(payment.providerReference());
            bus.publish(new OrderConfirmed(reservation.reservationId(), reservation.tokenId(),
                    reservation.tierId(), reservation.quantity(), reservation.totalAmount()));
            return;
        }
        if (reservation.state() == ReservationState.CONFIRMED) {  // same payment twice, already counted
            return;
        }
        expireIfDue(reservation);  // the hold ran out while the bank was answering
        provider.voidAuthorisation(payment.providerReference());
        bus.publish(new PaymentVoided(reservation.reservationId(),
                payment.providerKey(), payment.providerReference()));
    }
}
