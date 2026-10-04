package th.ac.kmitl.sda.ticketqueue.core.registry;

/** Nobody registered a payment plug-in under this key. */
public final class UnknownPaymentProviderException extends RuntimeException {

    public UnknownPaymentProviderException(String providerKey) {
        super("No payment provider registered for key \"" + providerKey + "\"");
    }
}
