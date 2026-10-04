package th.ac.kmitl.sda.ticketqueue.core.registry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import th.ac.kmitl.sda.ticketqueue.contract.PaymentProvider;

/**
 * Key → plug-in. Slide 8.2's fix for the {@code if (method.equals("stripe"))}
 * chain: the core looks a plug-in up by key and never names its class.
 */
public final class PluginRegistry {

    private final Map<String, PaymentProvider> paymentProviders = new ConcurrentHashMap<>();

    public void register(PaymentProvider provider) {
        paymentProviders.put(provider.key(), provider);
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
