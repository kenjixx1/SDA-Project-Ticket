package th.ac.kmitl.sda.ticketqueue.plugin.payment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import th.ac.kmitl.sda.ticketqueue.contract.PaymentProvider;
import th.ac.kmitl.sda.ticketqueue.contract.PaymentRequest;
import th.ac.kmitl.sda.ticketqueue.contract.PaymentResult;

// Fake PromptPay: always says yes, no network. The same key gives the same payment.
public final class PromptPayPlugin implements PaymentProvider {

    public enum Status { AUTHORISED, CAPTURED, VOIDED }

    private final Map<String, String> referenceByKey = new ConcurrentHashMap<>();
    private final Map<String, Status> statusByReference = new ConcurrentHashMap<>();

    @Override
    public String key() {
        return "promptpay";
    }

    @Override
    public PaymentResult authorise(PaymentRequest request) {
        String reference = referenceByKey.computeIfAbsent(request.idempotencyKey(), k -> {
            String ref = "PP-" + request.reservationId();
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
