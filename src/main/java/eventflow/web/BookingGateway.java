package eventflow.web;

import eventflow.web.dto.Views.EventDetail;
import eventflow.web.dto.Views.EventSummary;
import eventflow.web.dto.Views.HoldView;
import eventflow.web.dto.Views.JoinResult;
import eventflow.web.dto.Views.Me;
import eventflow.web.dto.Views.OrderView;
import eventflow.web.dto.Views.PaymentStart;
import eventflow.web.dto.Views.PaymentStatus;
import eventflow.web.dto.Views.QueueStatus;
import eventflow.web.dto.Views.TierAvailability;

import java.util.List;

/**
 * Everything the customer pages can ask for. The presentation layer's own adapter, not a plug-in seam.
 * Filled by FakeBookingGateway today and by CoreBookingGateway when core/ is ready.
 * Every method may throw GatewayException.
 */
public interface BookingGateway {

    /** The server's clock, epoch milliseconds. */
    long now();

    /** How fast the server clock runs compared with real time. The real system is always 1. */
    default int clockRate() {
        return 1;
    }

    List<EventSummary> listEvents(String customerId);                                      // M-01

    EventDetail getEvent(String customerId, String eventId);                               // M-02

    JoinResult joinQueue(String customerId, String eventId);                               // M-03

    QueueStatus queueStatus(String customerId, String token);                              // M-04

    List<TierAvailability> availability(String eventId);                                  // M-05

    HoldView hold(String customerId, String token, String tierId, int quantity);           // M-06

    HoldView getHold(String customerId, String reservationId);                             // M-07

    HoldView release(String customerId, String reservationId);                             // M-08

    PaymentStart startPayment(String customerId, String reservationId, String providerKey); // M-09

    PaymentStatus answerPayment(String customerId, String paymentId, boolean approve);     // M-10

    PaymentStatus paymentStatus(String customerId, String paymentId);                      // M-11

    List<OrderView> orders(String customerId);                                             // M-12

    Me me(String customerId);                                                              // M-13
}
