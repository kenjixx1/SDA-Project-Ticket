package th.ac.kmitl.sda.ticketqueue.core.registry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import th.ac.kmitl.sda.ticketqueue.contract.PaymentProvider;

// Finds plug-ins by key, so the core never names a plug-in class.
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
