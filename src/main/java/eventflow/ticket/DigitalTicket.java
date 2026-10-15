package eventflow.ticket;

import java.time.Instant;

// One ticket for one seat. 2 seats bought = 2 tickets.
public record DigitalTicket(String ticketId, String reservationId, String eventId, String tierId,
                            int n, int of, String payload, String signature, Instant issuedAt) {

    // What goes in the QR code: the details plus the signature that proves we made it.
    public String qrText() {
        return payload + "|" + signature;
    }
}
