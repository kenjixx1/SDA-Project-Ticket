package eventflow.contract;

// Every promotion plugs in through this interface.
public interface PricingRule {

    String key();

    String name();  // shown on the receipt, e.g. "Early Bird −10%"

    boolean appliesTo(PricingRequest request);

    Money apply(Money currentTotal, PricingRequest request);
}
