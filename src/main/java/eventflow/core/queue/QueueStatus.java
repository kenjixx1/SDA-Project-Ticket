package eventflow.core.queue;

import java.time.Duration;

public record QueueStatus(QueueTokenState state, long position, Duration estimatedWait) {
}
