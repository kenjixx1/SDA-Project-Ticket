package eventflow.ticket;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import eventflow.core.event.EventBus;
import eventflow.core.event.OrderConfirmed;
import eventflow.core.event.TicketIssued;
import eventflow.core.inventory.InventoryService;

// Issues tickets when an order is confirmed, and checks a ticket's QR code is genuine.
public final class TicketService {

    private final Map<String, List<DigitalTicket>> byReservation = new ConcurrentHashMap<>();
    private final InventoryService inventory;
    private final EventBus bus;
    private final Clock clock;
    private final SecretKeySpec key;

    public TicketService(InventoryService inventory, EventBus bus, Clock clock, byte[] secret) {
        this.inventory = inventory;
        this.bus = bus;
        this.clock = clock;
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        bus.subscribe(OrderConfirmed.class, this::onOrderConfirmed);
    }

    public List<DigitalTicket> ticketsFor(String reservationId) {
        return byReservation.getOrDefault(reservationId, List.of());
    }

    // True only if nobody changed the details since we signed them.
    public boolean verify(String qrText) {
        int cut = qrText.lastIndexOf('|');
        return cut > 0 && sign(qrText.substring(0, cut)).equals(qrText.substring(cut + 1));
    }

    private void onOrderConfirmed(OrderConfirmed order) {
        if (byReservation.containsKey(order.reservationId())) {
            return;  // the same event twice is ignored
        }
        String eventId = inventory.tier(order.tierId()).eventId();
        String serial = order.reservationId().replace("R-", "");
        List<DigitalTicket> tickets = new ArrayList<>();
        for (int n = 1; n <= order.quantity(); n++) {
            String ticketId = String.format("TKT-%s-%02d", serial, n);
            String payload = String.join("|", "EVENTFLOW", ticketId, eventId, order.tierId(), order.tokenId());
            tickets.add(new DigitalTicket(ticketId, order.reservationId(), eventId, order.tierId(),
                    n, order.quantity(), payload, sign(payload), clock.instant()));
        }
        byReservation.put(order.reservationId(), List.copyOf(tickets));
        bus.publish(new TicketIssued(order.reservationId(), order.tokenId(),
                tickets.stream().map(DigitalTicket::ticketId).toList()));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);  // 16 hex characters is enough for a QR
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
