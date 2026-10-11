package th.ac.kmitl.sda.ticketqueue.core.queue;

import java.time.Duration;

/**
 * What Kenji's queue screen shows: his place and a rough wait.
 * Position and wait are 0 once he is no longer waiting.
 */
public record QueueStatus(QueueTokenState state, long position, Duration estimatedWait) {
}
