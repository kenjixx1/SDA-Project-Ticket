package th.ac.kmitl.sda.ticketqueue.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The event channel. Publishers do not know who is listening.
 *
 * <p>Broker topology: there is no central controller. Each listener does its
 * one job and may publish a new event.
 *
 * <p>Delivery is in-process and on the publisher's thread, so when
 * {@link #publish} returns, every listener has run.
 */
public final class EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    private final List<DomainEvent> history = new CopyOnWriteArrayList<>();

    public <E extends DomainEvent> void subscribe(Class<E> type, Consumer<? super E> listener) {
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>())
                 .add(event -> listener.accept(type.cast(event)));
    }

    public void publish(DomainEvent event) {
        history.add(event);
        for (Consumer<Object> listener : listeners.getOrDefault(event.getClass(), List.of())) {
            listener.accept(event);
        }
    }

    /** Every event published so far, in order. For the demo, the dashboard and tests. */
    public List<DomainEvent> history() {
        return List.copyOf(history);
    }
}
