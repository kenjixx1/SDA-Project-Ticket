package th.ac.kmitl.sda.ticketqueue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import th.ac.kmitl.sda.ticketqueue.admin.Event;
import th.ac.kmitl.sda.ticketqueue.contract.Money;
import th.ac.kmitl.sda.ticketqueue.core.inventory.TicketTier;
import th.ac.kmitl.sda.ticketqueue.core.queue.QueueTokenState;

// Test data: BLACKPINK at Impact Arena, as in the docs.
final class Fixtures {

    private Fixtures() {
    }

    static SettableClock clock() {
        return new SettableClock(
                ZonedDateTime.of(2026, 10, 1, 10, 4, 40, 0, SettableClock.BANGKOK).toInstant());
    }

    static Event blackpink(EventFlow app) {
        return app.events().createEvent("BLACKPINK — Bangkok", "Impact Arena",
                LocalDate.of(2026, 11, 14), LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    static TicketTier vip(EventFlow app, int seats) {
        String eventId = blackpink(app).eventId();
        TicketTier tier = app.events().addTier(eventId, "VIP", Money.baht(6_500), seats);
        app.events().openSale(eventId);
        return tier;
    }

    static String admit(EventFlow app, TicketTier tier, String customerId) {
        String tokenId = app.queue().join(customerId, tier.eventId()).token().tokenId();
        while (app.queue().token(tokenId).state() == QueueTokenState.WAITING) {
            app.admission().admitBatch(tier.eventId());
        }
        return tokenId;
    }

    static List<String> admitFans(EventFlow app, TicketTier tier, int count) {
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            tokens.add(app.queue().join("fan-" + i, tier.eventId()).token().tokenId());
        }
        while (app.queue().queueLength(tier.eventId()) > 0) {
            app.admission().admitBatch(tier.eventId());
        }
        return tokens;
    }
}
