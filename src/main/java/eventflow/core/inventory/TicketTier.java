package eventflow.core.inventory;

import eventflow.contract.Money;

public final class TicketTier {

    private final String tierId;
    private final String eventId;
    private String name;
    private Money price;
    private int totalQuantity;
    private int heldQuantity;
    private int soldQuantity;

    TicketTier(String tierId, String eventId, String name, Money price, int totalQuantity) {
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("A tier needs at least one seat");
        }
        this.tierId = tierId;
        this.eventId = eventId;
        this.name = name;
        this.price = price;
        this.totalQuantity = totalQuantity;
    }

    // Only InventoryService can change these counts.
    synchronized void hold(int quantity)    { heldQuantity += quantity; }
    synchronized void release(int quantity) { heldQuantity -= quantity; }
    synchronized void sell(int quantity)    { heldQuantity -= quantity; soldQuantity += quantity; }
    synchronized void rename(String newName)   { name = newName; }
    synchronized void reprice(Money newPrice)  { price = newPrice; }

    synchronized void resize(int newTotal) {
        if (newTotal < heldQuantity + soldQuantity) {
            throw new BelowHeldSoldException(heldQuantity, soldQuantity);
        }
        totalQuantity = newTotal;
    }

    public synchronized int remaining() {
        return totalQuantity - heldQuantity - soldQuantity;
    }

    public String tierId()  { return tierId; }
    public String eventId() { return eventId; }
    public synchronized String name()  { return name; }
    public synchronized Money price()  { return price; }
    public synchronized int totalQuantity() { return totalQuantity; }
    public synchronized int heldQuantity()  { return heldQuantity; }
    public synchronized int soldQuantity()  { return soldQuantity; }

    @Override
    public synchronized String toString() {
        return String.format("%-7s %s  total %,d · held %,d · sold %,d · remaining %,d",
                name, price, totalQuantity, heldQuantity, soldQuantity, remaining());
    }
}
