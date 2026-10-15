package eventflow.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Event {

    private final String eventId;
    private volatile String name;
    private volatile String venue;
    private volatile LocalDate date;
    private volatile LocalDateTime saleOpensAt;
    private volatile boolean onSale;
    private volatile boolean closing;
    private volatile Instant closedAt;

    Event(String eventId, String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        this.eventId = eventId;
        this.name = name;
        this.venue = venue;
        this.date = date;
        this.saleOpensAt = saleOpensAt;
    }

    void openSale() { onSale = true; }
    void startClosing() { closing = true; }
    void markClosed(Instant at) { closedAt = at; }

    void changeDetails(String newName, String newVenue, LocalDate newDate, LocalDateTime newSaleOpensAt) {
        name = newName;
        venue = newVenue;
        date = newDate;
        saleOpensAt = newSaleOpensAt;
    }

    public String eventId()            { return eventId; }
    public String name()               { return name; }
    public String venue()              { return venue; }
    public LocalDate date()            { return date; }
    public LocalDateTime saleOpensAt() { return saleOpensAt; }
    public boolean onSale()            { return onSale; }
    boolean closing()                  { return closing; }
    public Instant closedAt()          { return closedAt; }
}
