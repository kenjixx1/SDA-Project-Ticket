package eventflow.contract;

// Every payment method plugs in through this interface.
public interface PaymentProvider {

    String key();

    PaymentResult authorise(PaymentRequest request);

    void capture(String providerReference);

    void voidAuthorisation(String providerReference);  // Used when the hold expired before the bank answered.
}
