package eventflow.core.registry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import eventflow.contract.PaymentProvider;
import eventflow.contract.PricingRule;

// Finds plug-ins by key, so the core never names a plug-in class.
public final class PluginRegistry {

    private final Map<String, PaymentProvider> paymentProviders = new ConcurrentHashMap<>();
    private final Map<String, PricingRule> pricingRules = new ConcurrentHashMap<>();

    public void register(PaymentProvider provider) {
        paymentProviders.put(provider.key(), provider);
    }

    public void register(PricingRule rule) {
        pricingRules.put(rule.key(), rule);
    }

    // Sorted by key, so the same order always gives the same total.
    public List<PricingRule> pricingRules() {
        return pricingRules.values().stream().sorted(Comparator.comparing(PricingRule::key)).toList();
    }

    public PaymentProvider payment(String providerKey) {
        PaymentProvider provider = paymentProviders.get(providerKey);
        if (provider == null) {
            throw new UnknownPaymentProviderException(providerKey);
        }
        return provider;
    }

    public Set<String> paymentKeys() {
        return Set.copyOf(paymentProviders.keySet());
    }
}
