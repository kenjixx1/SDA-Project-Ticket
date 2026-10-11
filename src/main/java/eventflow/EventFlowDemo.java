package eventflow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import eventflow.admin.Event;
import eventflow.contract.Money;
import eventflow.core.event.DomainEvent;
import eventflow.core.inventory.HoldResult;
import eventflow.core.inventory.TicketTier;
import eventflow.core.event.UserAdmitted;
import eventflow.core.order.CheckoutResult;
import eventflow.core.queue.JoinResult;
import eventflow.core.queue.QueueStatus;
import eventflow.core.queue.QueueTokenState;

// Kenji's purchase from the docs, start to finish.
public final class EventFlowDemo {

    public static void main(String[] args) {
        SettableClock clock = new SettableClock(
                ZonedDateTime.of(2026, 10, 1, 10, 4, 40, 0, SettableClock.BANGKOK).toInstant());
        EventFlow app = new EventFlow(clock);

        title("Chai creates the event");
        Event concert = app.events().createEvent("BLACKPINK — Bangkok", "Impact Arena",
                LocalDate.of(2026, 11, 14), LocalDateTime.of(2026, 10, 1, 10, 0));
        TicketTier vip = app.events().addTier(concert.eventId(), "VIP", Money.baht(6_500), 500);
        app.events().addTier(concert.eventId(), "Zone A", Money.baht(3_800), 2_000);
        app.events().addTier(concert.eventId(), "Zone B", Money.baht(1_900), 5_000);
        app.events().openSale(concert.eventId());
        printTiers(app, concert);

        title("4,181 fans join first, then Kenji");
        String firstFan = null;
        for (int i = 1; i <= 4_181; i++) {
            String token = app.queue().join("fan-" + i, concert.eventId()).token().tokenId();
            firstFan = firstFan == null ? token : firstFan;
        }
        JoinResult joined = app.queue().join("kenji", concert.eventId());
        String kenjiToken = joined.token().tokenId();
        QueueStatus place = app.queue().status(kenjiToken);
        say("join: " + joined.outcome() + " · token " + kenjiToken
                + " · position " + String.format("%,d", place.position())
                + " · about " + place.estimatedWait().toSeconds() + " s at "
                + app.queue().admissionRate(concert.eventId()) + " per second");
        say("Kenji opens a second tab: " + app.queue().join("kenji", concert.eventId()).outcome()
                + ", same token " + app.queue().join("kenji", concert.eventId()).token().tokenId());

        title("The waiting room admits a batch every second");
        int batches = 0;
        while (app.queue().token(kenjiToken).state() == QueueTokenState.WAITING) {
            app.admission().admitBatch(concert.eventId());
            batches++;
            if (batches % 7 == 0 || app.queue().token(kenjiToken).state() != QueueTokenState.WAITING) {
                say("after batch " + batches + ": Kenji is "
                        + describe(app.queue().status(kenjiToken)));
            }
        }

        title("Kenji tries 5 seats (the limit is 4)");
        say("hold: " + app.inventory().hold(kenjiToken, vip.tierId(), 5).outcome());

        title("Kenji holds 2 × VIP");
        HoldResult kenji = app.inventory().hold(kenjiToken, vip.tierId(), 2);
        say("hold: " + kenji.outcome() + " · " + kenji.reservation()
                + " · expires " + time(clock, kenji.reservation().expiresAt()));
        say("VIP now: " + vip);

        say("the same token again: " + app.inventory().hold(kenjiToken, vip.tierId(), 2).outcome());

        title("Kenji pays with PromptPay");
        CheckoutResult paid = app.checkout().checkout(kenji.reservation().reservationId(), "promptpay");
        say("checkout: " + paid + " · total " + kenji.reservation().totalAmount());
        say("VIP now: " + vip);

        title("Fan #1 holds 2 × VIP and walks away");
        HoldResult walkAway = app.inventory().hold(firstFan, vip.tierId(), 2);
        say("hold: " + walkAway.outcome() + " · VIP now: " + vip);
        clock.advance(Duration.ofMinutes(10));
        say("… 10 minutes later (" + time(clock, clock.instant()) + "), the expiry sweep runs");
        say("expired holds: " + app.expiry().sweep());
        say("VIP now: " + vip);
        say("trying to pay now: "
                + app.checkout().checkout(walkAway.reservation().reservationId(), "promptpay"));

        title("Every event through the broker, in order");
        long admissions = 0;
        for (DomainEvent e : app.bus().history()) {
            if (e instanceof UserAdmitted) {
                admissions++;
                continue;
            }
            if (admissions > 0) {
                say(String.format("UserAdmitted × %,d", admissions));
                admissions = 0;
            }
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

    private static String describe(QueueStatus status) {
        return status.state() == QueueTokenState.WAITING
                ? String.format("at position %,d", status.position())
                : status.state().toString();
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
