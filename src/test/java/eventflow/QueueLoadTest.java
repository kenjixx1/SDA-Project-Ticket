package eventflow;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

import eventflow.core.inventory.TicketTier;

// 40,000 fans join at once. Measured inside Java, not over a network.
public final class QueueLoadTest {

    private static final int CROWD = 40_000;
    private static final int THREADS = 64;

    @Test
    public void fortyThousandJoinAtOnce_everyoneGetsATokenInTime() throws Exception {
        EventFlow app = new EventFlow(Fixtures.clock());
        TicketTier vip = Fixtures.vip(app, 500);
        Set<String> tokens = ConcurrentHashMap.newKeySet();
        Set<Long> places = ConcurrentHashMap.newKeySet();

        Timing join = run(i -> {
            var result = app.queue().join("fan-" + i, vip.eventId());
            tokens.add(result.token().tokenId());
            places.add(result.token().sequence());
        });
        Timing position = run(i -> app.queue().status("Q-" + (i + 1)));

        System.out.printf("      issue token:     avg %.3f ms · max %.1f ms%n", join.avgMs(), join.maxMs());
        System.out.printf("      refresh position: avg %.3f ms · max %.1f ms%n", position.avgMs(), position.maxMs());

        Check.equal(CROWD, tokens.size(), "everyone got a token");
        Check.equal(CROWD, places.size(), "every place is different");
        Check.equal(CROWD, app.queue().queueLength(vip.eventId()), "everyone is in line");
        Check.that(join.avgMs() <= 200 && join.maxMs() <= 1_000,
                "issue token: avg ≤ 200 ms, max ≤ 1 s — got " + join);
        Check.that(position.avgMs() <= 1_000 && position.maxMs() <= 5_000,
                "refresh position: avg ≤ 1 s, max ≤ 5 s — got " + position);
    }

    private interface Request {
        void call(int i) throws Exception;
    }

    private record Timing(double avgMs, double maxMs) {
    }

    private static Timing run(Request request) throws Exception {
        AtomicLong totalNanos = new AtomicLong();
        LongAccumulator maxNanos = new LongAccumulator(Math::max, 0);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < CROWD; i++) {
            int n = i;
            pool.submit(() -> {
                go.await();
                long start = System.nanoTime();
                request.call(n);
                long took = System.nanoTime() - start;
                totalNanos.addAndGet(took);
                maxNanos.accumulate(took);
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        Check.that(pool.awaitTermination(60, TimeUnit.SECONDS), "crowd did not finish in 60 s");
        return new Timing(totalNanos.get() / 1e6 / CROWD, maxNanos.get() / 1e6);
    }
}
