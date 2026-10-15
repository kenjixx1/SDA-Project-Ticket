package eventflow.core.order;

import java.util.ArrayList;
import java.util.List;

import eventflow.contract.Money;
import eventflow.contract.PricingRequest;
import eventflow.contract.PricingRule;
import eventflow.contract.Promotion;
import eventflow.core.registry.PluginRegistry;

// Works out what a hold costs: tier price × quantity, then every pricing rule that applies.
public final class PricingService {

    public record Price(List<Promotion> promotions, Money total) {
    }

    private final PluginRegistry registry;

    public PricingService(PluginRegistry registry) {
        this.registry = registry;
    }

    public Price price(PricingRequest request) {
        Money total = request.unitPrice().times(request.quantity());
        List<Promotion> applied = new ArrayList<>();
        for (PricingRule rule : registry.pricingRules()) {
            if (rule.appliesTo(request)) {
                Money after = rule.apply(total, request);
                applied.add(new Promotion(rule.name(), total.minus(after)));
                total = after;
            }
        }
        return new Price(applied, total);
    }
}
