package eventflow.plugin.payment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eventflow.contract.PaymentProvider;
import eventflow.contract.PaymentRequest;
import eventflow.contract.PaymentResult;

// Fake card payment (like Stripe): always says yes, no network, no card numbers.
public final class CardPlugin implements PaymentProvider {

    public enum Status { AUTHORISED, CAPTURED, VOIDED }

    private final Map<String, String> referenceByKey = new ConcurrentHashMap<>();
    private final Map<String, Status> statusByReference = new ConcurrentHashMap<>();

    @Override
    public String key() {
        return "card";
    }

    @Override
    public PaymentResult authorise(PaymentRequest request) {
        String reference = referenceByKey.computeIfAbsent(request.idempotencyKey(), k -> {
            String ref = "CARD-" + request.reservationId();
            statusByReference.put(ref, Status.AUTHORISED);
            return ref;
        });
        return PaymentResult.authorised(reference);
    }

    @Override
    public void capture(String providerReference) {
        statusByReference.replace(providerReference, Status.AUTHORISED, Status.CAPTURED);
    }

    @Override
    public void voidAuthorisation(String providerReference) {
        statusByReference.replace(providerReference, Status.AUTHORISED, Status.VOIDED);
    }

    public Status status(String providerReference) {
        return statusByReference.get(providerReference);
    }
}
