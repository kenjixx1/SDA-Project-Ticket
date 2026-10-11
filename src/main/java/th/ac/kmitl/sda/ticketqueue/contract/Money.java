package th.ac.kmitl.sda.ticketqueue.contract;

public record Money(long baht) {

    public Money {
        if (baht < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + baht);
        }
    }

    public static Money baht(long baht) {
        return new Money(baht);
    }

    public Money times(int quantity) {
        return new Money(baht * quantity);
    }

    public Money plus(Money other) {
        return new Money(baht + other.baht);
    }

    @Override
    public String toString() {
        return String.format("฿%,d", baht);
    }
}
