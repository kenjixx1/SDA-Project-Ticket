package eventflow;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import eventflow.admin.ChangeRefused;
import eventflow.admin.EventState;
import eventflow.admin.SalesDashboardService.Dashboard;
import eventflow.contract.Money;
import eventflow.core.inventory.HoldResult;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.CheckoutResult;
import eventflow.core.order.Reservation;
import eventflow.core.queue.JoinResult;
import eventflow.core.queue.QueueTokenState;

public final class OrganizerTest {

    @Test
    public void beforeTheSale_priceCanChange() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = scheduledVip(app);

        app.events().changeTier(vip.eventId(), vip.tierId(), "VIP", Money.baht(7_000), 500);

        Check.equal(Money.baht(7_000), vip.price(), "new price");
    }

    @Test
    public void duringTheSale_priceIsFrozen() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);

        ChangeRefused e = refused(() ->
                app.events().changeTier(vip.eventId(), vip.tierId(), "VIP", Money.baht(7_200), 500));

        Check.equal(ChangeRefused.Reason.PRICE_FROZEN, e.reason(), "reason");
        Check.equal(Money.baht(6_500), vip.price(), "price unchanged");
    }

    @Test
    public void duringTheSale_seatsCanBeAdded() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);

        app.events().changeTier(vip.eventId(), vip.tierId(), "VIP", Money.baht(6_500), 600);

        Check.equal(600, vip.totalQuantity(), "total");
        Check.equal(600, vip.remaining(), "remaining");
    }

    @Test
    public void cannotReduceBelowHeldPlusSold() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation paid = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();
        app.checkout().checkout(paid.reservationId(), "promptpay");
        app.inventory().hold(Fixtures.admit(app, vip, "mint"), vip.tierId(), 3);

        ChangeRefused e = refused(() ->
                app.events().changeTier(vip.eventId(), vip.tierId(), "VIP", Money.baht(6_500), 4));

        Check.equal(ChangeRefused.Reason.BELOW_HELD_SOLD, e.reason(), "reason");
        Check.equal("VIP: 2 sold and 3 held. Cannot reduce below 5.", e.getMessage(), "message");
        app.events().changeTier(vip.eventId(), vip.tierId(), "VIP", Money.baht(6_500), 5);
        Check.equal(0, vip.remaining(), "down to exactly held + sold");
        Check.invariants(app, vip);
    }

    @Test
    public void closingTheSale_letsNobodyNewIn() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        String admitted = Fixtures.admit(app, vip, "kenji");
        String waiting = app.queue().join("mint", vip.eventId()).token().tokenId();

        app.events().closeSale(vip.eventId());

        Check.equal(JoinResult.Outcome.SALE_CLOSED,
                app.queue().join("fan-9", vip.eventId()).outcome(), "new fans");
        Check.equal(QueueTokenState.EXPIRED, app.queue().token(waiting).state(), "fan still waiting");
        Check.equal(0, app.queue().queueLength(vip.eventId()), "queue emptied");
        Check.equal(HoldResult.Outcome.SALE_CLOSED,
                app.inventory().hold(admitted, vip.tierId(), 2).outcome(), "admitted but no hold yet");
        Check.equal(EventState.CLOSED, app.events().state(vip.eventId()), "nothing held, so closed at once");
    }

    @Test
    public void closingTheSale_liveHoldsKeepTheirTime() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();

        app.events().closeSale(vip.eventId());

        Check.equal(EventState.CLOSING, app.events().state(vip.eventId()), "a hold is still running");
        Check.equal(CheckoutResult.CONFIRMED, app.checkout().checkout(r.reservationId(), "promptpay"),
                "Kenji can still pay inside his 10 minutes");
        Check.equal(EventState.CLOSED, app.events().state(vip.eventId()), "closed once the hold resolved");
        Check.that(app.events().event(vip.eventId()).closedAt() != null, "closedAt stamped");
    }

    @Test
    public void closingTheSale_anExpiringHoldAlsoFinishesIt() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2);
        app.events().closeSale(vip.eventId());

        clock.advance(Duration.ofMinutes(10));
        app.expiry().sweep();

        Check.equal(EventState.CLOSED, app.events().state(vip.eventId()), "state");
        Check.equal(500, vip.remaining(), "seats returned, though nobody new can buy them");
    }

    @Test
    public void afterClosing_nothingCanChange() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        app.events().closeSale(vip.eventId());

        Check.equal(ChangeRefused.Reason.NOT_ON_SALE,
                refused(() -> app.events().closeSale(vip.eventId())).reason(), "close twice");
        Check.equal(ChangeRefused.Reason.EVENT_LOCKED, refused(() ->
                app.events().changeTier(vip.eventId(), vip.tierId(), "VIP", Money.baht(6_500), 600)).reason(),
                "add seats");
        Check.equal(ChangeRefused.Reason.EVENT_LOCKED, refused(() ->
                app.events().addTier(vip.eventId(), "Zone C", Money.baht(900), 100)).reason(), "new tier");
        Check.equal(ChangeRefused.Reason.EVENT_LOCKED, refused(() ->
                app.events().setAdmissionRate(vip.eventId(), 50)).reason(), "admission rate");
    }

    @Test
    public void duringTheSale_detailsChangeButNotTheSaleTime() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        var event = app.events().event(vip.eventId());

        app.events().changeDetails(vip.eventId(), "The 1975 Bangkok — Night 1", event.venue(), event.date(),
                event.saleOpensAt());
        ChangeRefused e = refused(() -> app.events().changeDetails(vip.eventId(), event.name(), event.venue(),
                event.date(), event.saleOpensAt().plusHours(1)));

        Check.equal("The 1975 Bangkok — Night 1", event.name(), "new name");
        Check.equal(ChangeRefused.Reason.SALE_TIME_LOCKED, e.reason(), "sale time");
    }

    @Test
    public void dashboard_showsQueueTiersAndRevenue() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation paid = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();
        app.checkout().checkout(paid.reservationId(), "promptpay");
        app.inventory().hold(Fixtures.admit(app, vip, "mint"), vip.tierId(), 3);
        app.queue().join("fan-1", vip.eventId());
        app.queue().join("fan-2", vip.eventId());

        Dashboard d = app.dashboard().dashboard(vip.eventId());

        Check.equal(EventState.ON_SALE, d.state(), "state");
        Check.equal(2, d.waiting(), "waiting");
        Check.equal(200, d.admissionRate(), "admission rate");
        Check.equal(2L, d.admitted(), "admitted so far");
        Check.equal(2, d.tiers().get(0).sold(), "VIP sold");
        Check.equal(3, d.tiers().get(0).held(), "VIP held");
        Check.equal(495, d.tiers().get(0).remaining(), "VIP remaining");
        Check.equal(500, d.all().held() + d.all().sold() + d.all().remaining(), "every row adds up");
        Check.equal(Money.baht(11_700), d.revenue(), "revenue: confirmed orders only, after Early Bird");
        Check.equal(1L, d.ordersConfirmed(), "orders");
    }

    private static TicketTier scheduledVip(EventFlow app) {
        var e = app.events().createEvent("The 1975 Bangkok", "Impact Arena", LocalDate.of(2026, 11, 14),
                LocalDateTime.of(2026, 10, 1, 10, 0));
        return app.events().addTier(e.eventId(), "VIP", Money.baht(6_500), 500);
    }

    private static ChangeRefused refused(Runnable change) {
        try {
            change.run();
        } catch (ChangeRefused e) {
            return e;
        }
        throw new AssertionError("expected the change to be refused");
    }
}
