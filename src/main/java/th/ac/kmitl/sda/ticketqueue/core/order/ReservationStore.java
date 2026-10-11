package th.ac.kmitl.sda.ticketqueue.core.order;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every reservation, by id. Stands in for the single shared database. The
 * database is the truth; events are only announcements.
 */
public final class ReservationStore {

    private final Map<String, Reservation> byId = new ConcurrentHashMap<>();

    public void add(Reservation reservation) {
        byId.put(reservation.reservationId(), reservation);
    }

    public Optional<Reservation> find(String reservationId) {
        return Optional.ofNullable(byId.get(reservationId));
    }

    public List<Reservation> all() {
        return List.copyOf(byId.values());
    }

    public List<Reservation> inState(ReservationState state) {
        return byId.values().stream().filter(r -> r.state() == state).toList();
    }
}
