package eventflow;

import java.time.Duration;

import eventflow.contract.Money;
import eventflow.contract.PricingRequest;
import eventflow.contract.PricingRule;
import eventflow.core.inventory.TicketTier;
import eventflow.core.order.Reservation;

public final class PricingTest {

    @Test
    public void heldInFirst30Minutes_earlyBirdTakes10PercentOff() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        clock.advance(Duration.ofMinutes(29));

        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        Check.equal(Money.baht(11_700), r.totalAmount(), "total");
        Check.equal("Early Bird −10%", r.promotions().get(0).name(), "promotion line");
        Check.equal(Money.baht(1_300), r.promotions().get(0).amount(), "discount");
    }

    @Test
    public void heldAfter30Minutes_fullPrice() {
        SettableClock clock = Fixtures.clock();
        EventFlow app = new EventFlow(clock);
        TicketTier vip = Fixtures.vip(app, 500);
        String kenji = Fixtures.admit(app, vip, "kenji");
        clock.advance(Duration.ofMinutes(30));

        Reservation r = app.inventory().hold(kenji, vip.tierId(), 2).reservation();

        Check.equal(Money.baht(13_000), r.totalAmount(), "total");
        Check.equal(0, r.promotions().size(), "no promotion");
    }

    @Test
    public void newPromotion_pluggedInWithoutCoreChanges() {
        EventFlow app = new EventFlow(Fixtures.clock());
        app.registry().register(new PricingRule() {  // a new promotion, written only here
            public String key() { return "fan-club"; }
            public String name() { return "Fan Club −฿500"; }
            public boolean appliesTo(PricingRequest request) { return true; }
            public Money apply(Money total, PricingRequest request) { return total.minus(Money.baht(500)); }
        });
        TicketTier vip = Fixtures.vip(app, 500);

        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();

        Check.equal(Money.baht(11_200), r.totalAmount(), "13,000 − 1,300 early bird − 500 fan club");
        Check.equal(2, r.promotions().size(), "two promotion lines");
    }

    @Test
    public void userPaysTheDiscountedTotal() {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Reservation r = app.inventory().hold(Fixtures.admit(app, vip, "kenji"), vip.tierId(), 2).reservation();

        app.checkout().checkout(r.reservationId(), "promptpay");

        Check.that(app.bus().history().stream()
                .filter(e -> e instanceof eventflow.core.event.PaymentCompleted)
                .map(e -> ((eventflow.core.event.PaymentCompleted) e).amount())
                .allMatch(Money.baht(11_700)::equals), "PromptPay was asked for ฿11,700");
    }
}
