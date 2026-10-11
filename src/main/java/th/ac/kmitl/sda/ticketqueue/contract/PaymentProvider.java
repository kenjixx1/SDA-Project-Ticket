package th.ac.kmitl.sda.ticketqueue.contract;

/**
 * Seam 1 — how money is taken. One implementation per payment method.
 *
 * <p>The core finds a provider by {@link #key()} through the plug-in registry
 * and never names a provider class.
 */
public interface PaymentProvider {

    /** The lookup key, e.g. {@code "promptpay"}. */
    String key();

    /** Asks the provider to authorise. Same idempotency key = same payment, charged once. */
    PaymentResult authorise(PaymentRequest request);

    /** Takes the authorised money. Called only after the order is confirmed. */
    void capture(String providerReference);

    /** Cancels an authorisation whose hold expired first. */
    void voidAuthorisation(String providerReference);
}
