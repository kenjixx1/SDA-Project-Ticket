package eventflow.core.order;

import java.time.Clock;

import eventflow.contract.PaymentProvider;
import eventflow.contract.PaymentRequest;
import eventflow.contract.PaymentResult;
import eventflow.core.event.EventBus;
import eventflow.core.event.PaymentCompleted;
import eventflow.core.registry.PluginRegistry;

public final class CheckoutService {

    private final ReservationStore store;
    private final ReservationService reservations;
    private final PluginRegistry registry;
    private final EventBus bus;
    private final Clock clock;

    public CheckoutService(ReservationStore store, ReservationService reservations,
                           PluginRegistry registry, EventBus bus, Clock clock) {
        this.store = store;
        this.reservations = reservations;
        this.registry = registry;
        this.bus = bus;
        this.clock = clock;
    }

    public CheckoutResult checkout(String reservationId, String providerKey) {
        Reservation reservation = store.find(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("No reservation " + reservationId));

        switch (reservation.state()) {
            case CONFIRMED:  // already paid: a double tap is not charged twice
                return CheckoutResult.CONFIRMED;
            case EXPIRED:
            case RELEASED:
                return CheckoutResult.HOLD_EXPIRED;
            default:
                break;
        }
        if (reservation.isDue(clock.instant())) {
            reservations.expireIfDue(reservation);
            return CheckoutResult.HOLD_EXPIRED;
        }

        PaymentProvider provider = registry.payment(providerKey);  // found by key, never by class name
        PaymentResult result = provider.authorise(new PaymentRequest(
                reservationId, reservation.idempotencyKey(), reservation.totalAmount()));
        if (!result.authorised()) {
            return CheckoutResult.DECLINED;
        }

        bus.publish(new PaymentCompleted(reservationId, providerKey,
                result.providerReference(), reservation.totalAmount()));
        return reservation.state() == ReservationState.CONFIRMED
                ? CheckoutResult.CONFIRMED
                : CheckoutResult.HOLD_EXPIRED;
    }
}
