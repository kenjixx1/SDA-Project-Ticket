package th.ac.kmitl.sda.ticketqueue.core.registry;

public final class UnknownPaymentProviderException extends RuntimeException {

    public UnknownPaymentProviderException(String providerKey) {
        super("No payment provider registered for key \"" + providerKey + "\"");
    }
}
