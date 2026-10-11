package eventflow.contract;

public record PaymentResult(boolean authorised, String providerReference, String declineReason) {

    public static PaymentResult authorised(String providerReference) {
        return new PaymentResult(true, providerReference, null);
    }

    public static PaymentResult declined(String reason) {
        return new PaymentResult(false, null, reason);
    }
}
