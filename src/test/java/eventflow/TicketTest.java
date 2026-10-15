package eventflow;

import java.util.List;

import eventflow.core.inventory.TicketTier;
import eventflow.core.order.Reservation;
import eventflow.ticket.DigitalTicket;

public final class TicketTest {

    @Test
    public void paidForTwoSeats_twoTickets() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();

        app.checkout().checkout(r.reservationId(), "promptpay");
        List<DigitalTicket> tickets = app.tickets().ticketsFor(r.reservationId());

        Check.equal(2, tickets.size(), "one ticket per seat");
        Check.that(!tickets.get(0).ticketId().equals(tickets.get(1).ticketId()), "each ticket has its own code");
        Check.equal(1, tickets.get(0).n(), "first ticket");
        Check.equal(2, tickets.get(1).of(), "of two");
    }

    @Test
    public void notPaid_noTickets() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();

        Check.equal(0, app.tickets().ticketsFor(r.reservationId()).size(), "tickets before paying");
    }

    @Test
    public void genuineTicketVerifies_changedTicketDoesNot() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();
        app.checkout().checkout(r.reservationId(), "promptpay");
        String qr = app.tickets().ticketsFor(r.reservationId()).get(0).qrText();

        Check.that(app.tickets().verify(qr), "the real ticket verifies");
        String forged = qr.replace(vip.tierId(), "T-99");  // someone edits the tier on the ticket
        Check.that(!app.tickets().verify(forged), "an edited ticket fails");
    }

    @Test
    public void payingTwice_ticketsIssuedOnce() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();

        app.checkout().checkout(r.reservationId(), "promptpay");
        app.checkout().checkout(r.reservationId(), "promptpay");

        Check.equal(2, app.tickets().ticketsFor(r.reservationId()).size(), "still two tickets");
    }
}
