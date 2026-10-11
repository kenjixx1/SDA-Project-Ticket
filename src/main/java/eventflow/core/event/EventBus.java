package eventflow.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// Passes each event to everyone listening for it. The sender never knows who listens.
public final class EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    private final List<DomainEvent> history = new CopyOnWriteArrayList<>();

    public <E extends DomainEvent> void subscribe(Class<E> type, Consumer<? super E> listener) {
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>())
                 .add(event -> listener.accept(type.cast(event)));
    }

    // Listeners run right away, on the same thread.
    public void publish(DomainEvent event) {
        history.add(event);
        for (Consumer<Object> listener : listeners.getOrDefault(event.getClass(), List.of())) {
            listener.accept(event);
        }
    }

    public List<DomainEvent> history() {
        return List.copyOf(history);
    }
}
