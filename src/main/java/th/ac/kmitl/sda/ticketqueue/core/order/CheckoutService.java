package th.ac.kmitl.sda.ticketqueue.core.order;

import java.time.Clock;

import th.ac.kmitl.sda.ticketqueue.contract.PaymentProvider;
import th.ac.kmitl.sda.ticketqueue.contract.PaymentRequest;
import th.ac.kmitl.sda.ticketqueue.contract.PaymentResult;
import th.ac.kmitl.sda.ticketqueue.core.event.EventBus;
import th.ac.kmitl.sda.ticketqueue.core.event.PaymentCompleted;
import th.ac.kmitl.sda.ticketqueue.core.registry.PluginRegistry;

/**
 * UC-03 · Checkout & Pay.
 *
 * <p>Calling the provider is a direct call — Kenji is waiting for the answer.
 * What happens after "authorised" is an event: {@link PaymentCompleted}.
 */
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
            case CONFIRMED:
                return CheckoutResult.CONFIRMED;  // a double tap; charged once (FR-18)
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

        PaymentProvider provider = registry.payment(providerKey);  // by key, never by class
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
