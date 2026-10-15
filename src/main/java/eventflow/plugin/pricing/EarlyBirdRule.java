package eventflow.plugin.pricing;

import java.time.Duration;

import eventflow.contract.Money;
import eventflow.contract.PricingRequest;
import eventflow.contract.PricingRule;

// 10% off seats held in the first 30 minutes after the sale opens.
public final class EarlyBirdRule implements PricingRule {

    public static final Duration WINDOW = Duration.ofMinutes(30);

    @Override
    public String key() {
        return "early-bird";
    }

    @Override
    public String name() {
        return "Early Bird −10%";
    }

    @Override
    public boolean appliesTo(PricingRequest request) {
        return request.saleOpenedAt() != null
                && request.heldAt().isBefore(request.saleOpenedAt().plus(WINDOW));
    }

    @Override
    public Money apply(Money currentTotal, PricingRequest request) {
        return currentTotal.percentOff(10);
    }
}
