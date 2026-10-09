package th.ac.kmitl.sda.ticketqueue.web.dto;

/** Every request body the page sends. Field names are the JSON names. */
public final class Requests {

    private Requests() {
    }

    public record HoldRequest(String token, String tierId, int quantity) {
    }

    public record PaymentRequest(String providerKey) {
    }

    public record AnswerRequest(boolean approve) {
    }

    public record ScenarioRequest(String scenario) {
    }

    public record SpeedRequest(int times) {
    }

    public record AdvanceRequest(long seconds) {
    }
}
