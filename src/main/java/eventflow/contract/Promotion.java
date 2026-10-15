package eventflow.contract;

// One pricing rule that changed the total, and by how much.
public record Promotion(String name, Money amount) {
}
