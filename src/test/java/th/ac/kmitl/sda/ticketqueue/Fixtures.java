package th.ac.kmitl.sda.ticketqueue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import th.ac.kmitl.sda.ticketqueue.admin.Event;
import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;

/** The one story from AGENTS.md §2: BLACKPINK at Impact Arena. */
final class Fixtures {

    private Fixtures() {
    }

    /** 10:04:40 on 1 October 2026 — the moment Kenji takes his hold. */
    static SettableClock clock() {
        return new SettableClock(
                ZonedDateTime.of(2026, 10, 1, 10, 4, 40, 0, SettableClock.BANGKOK).toInstant());
    }

    static Event blackpink(EventFlow app) {
        return app.events().createEvent("BLACKPINK — Bangkok", "Impact Arena",
                LocalDate.of(2026, 11, 14), LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    /** A VIP tier at ฿6,500 with the given number of seats. */
    static TicketTier vip(EventFlow app, int seats) {
        return app.events().addTier(blackpink(app).eventId(), "VIP", Money.baht(6_500), seats);
    }
}
