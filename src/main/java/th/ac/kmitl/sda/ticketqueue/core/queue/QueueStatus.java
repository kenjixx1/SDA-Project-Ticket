package th.ac.kmitl.sda.ticketqueue.core.queue;

import java.time.Duration;

public record QueueStatus(QueueTokenState state, long position, Duration estimatedWait) {
}
