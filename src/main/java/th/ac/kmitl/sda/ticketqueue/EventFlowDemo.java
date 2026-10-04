package th.ac.kmitl.sda.ticketqueue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import th.ac.kmitl.sda.ticketqueue.admin.Event;
import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.event.DomainEvent;
import th.ac.kmitl.sda.ticketqueue.core.inventory.HoldResult;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.order.CheckoutResult;

/** Runs the story from the docs: Chai sets up the sale, Kenji buys 2 × VIP, someone else's hold expires. */
public final class EventFlowDemo {

    public static void main(String[] args) {
        SettableClock clock = new SettableClock(
                ZonedDateTime.of(2026, 10, 1, 10, 4, 40, 0, SettableClock.BANGKOK).toInstant());
        EventFlow app = new EventFlow(clock);

        title("Chai creates the event (UC-05)");
        Event concert = app.events().createEvent("BLACKPINK — Bangkok", "Impact Arena",
                LocalDate.of(2026, 11, 14), LocalDateTime.of(2026, 10, 1, 10, 0));
        TicketTier vip = app.events().addTier(concert.eventId(), "VIP", Money.baht(6_500), 500);
        app.events().addTier(concert.eventId(), "Zone A", Money.baht(3_800), 2_000);
        app.events().addTier(concert.eventId(), "Zone B", Money.baht(1_900), 5_000);
        app.events().openSale(concert.eventId());
        printTiers(app, concert);

        title("Kenji holds 2 × VIP (UC-02)");
        HoldResult kenji = app.inventory().hold("token-kenji", vip.tierId(), 2);
        say("hold: " + kenji.outcome() + " · " + kenji.reservation()
                + " · expires " + time(clock, kenji.reservation().expiresAt()));
        say("VIP now: " + vip);

        title("Kenji tries 5 seats with another token (FR-11)");
        say("hold: " + app.inventory().hold("token-kenji-2", vip.tierId(), 5).outcome());

        title("Kenji pays with PromptPay (UC-03)");
        CheckoutResult paid = app.checkout().checkout(kenji.reservation().reservationId(), "promptpay");
        say("checkout: " + paid + " · total " + kenji.reservation().totalAmount());
        say("VIP now: " + vip);

        title("Another fan holds 2 × VIP and walks away");
        HoldResult walkAway = app.inventory().hold("token-fan", vip.tierId(), 2);
        say("hold: " + walkAway.outcome() + " · VIP now: " + vip);
        clock.advance(Duration.ofMinutes(10));
        say("… 10 minutes later (" + time(clock, clock.instant()) + "), the expiry sweep runs");
        say("expired holds: " + app.expiry().sweep());
        say("VIP now: " + vip);
        say("trying to pay now: "
                + app.checkout().checkout(walkAway.reservation().reservationId(), "promptpay"));

        title("Every event on the channel, in order");
        for (DomainEvent e : app.bus().history()) {
            say(e.toString());
        }
    }

    private static void printTiers(EventFlow app, Event concert) {
        say(concert.name() + " · " + concert.venue() + " · " + concert.date()
                + " · on sale: " + concert.onSale());
        for (TicketTier tier : app.inventory().tiersOf(concert.eventId())) {
            say(tier.toString());
        }
    }

    private static String time(SettableClock clock, java.time.Instant instant) {
        return instant.atZone(clock.getZone()).toLocalTime().toString();
    }

    private static void title(String text) {
        System.out.println();
        System.out.println("── " + text);
    }

    private static void say(String text) {
        System.out.println("   " + text);
    }
}
