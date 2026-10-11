package eventflow.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Event {

    private final String eventId;
    private final String name;
    private final String venue;
    private final LocalDate date;
    private final LocalDateTime saleOpensAt;
    private volatile boolean onSale;

    Event(String eventId, String name, String venue, LocalDate date, LocalDateTime saleOpensAt) {
        this.eventId = eventId;
        this.name = name;
        this.venue = venue;
        this.date = date;
        this.saleOpensAt = saleOpensAt;
    }

    void openSale() { onSale = true; }

    public String eventId()            { return eventId; }
    public String name()               { return name; }
    public String venue()              { return venue; }
    public LocalDate date()            { return date; }
    public LocalDateTime saleOpensAt() { return saleOpensAt; }
    public boolean onSale()            { return onSale; }
}
