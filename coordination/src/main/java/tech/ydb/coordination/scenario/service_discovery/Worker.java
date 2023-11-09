package tech.ydb.coordination.scenario.service_discovery;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import tech.ydb.coordination.CoordinationClient;
import tech.ydb.coordination.CoordinationSession;
import tech.ydb.coordination.SemaphoreLease;
import tech.ydb.core.UnexpectedResultException;

public class Worker {
    public static final String SEMAPHORE_NAME = "service-discovery-semaphore";
    private final CoordinationSession session;
    private final SemaphoreLease semaphore;

    private Worker(CoordinationSession session, SemaphoreLease semaphore) {
        this.session = session;
        this.semaphore = semaphore;
    }

    public static CompletableFuture<Worker> newWorkerAsync(CoordinationClient client, String fullPath, String endpoint,
                                                      Duration maxAttemptTimeout) {
        return client.createSession(fullPath).thenCompose(session -> {
            byte[] data = endpoint.getBytes(StandardCharsets.UTF_8);
            return session.acquireSemaphore(SEMAPHORE_NAME, 1, data, maxAttemptTimeout).thenApply(lease -> {
                if (lease.isValid()) {
                    return new Worker(session, lease);
                } else {
                    throw new UnexpectedResultException("The semaphore for Worker wasn't acquired.",
                            lease.getStatusFuture().join());
                }
            });
        });
    }

    public static Worker newWorker(CoordinationClient client, String fullPath, String endpoint,
                                                      Duration maxAttemptTimeout) {
        return newWorkerAsync(client, fullPath, endpoint, maxAttemptTimeout).join();
    }

    public CompletableFuture<Boolean> stopAsync() {
        CompletableFuture<Boolean> releaseFuture = semaphore.release();
        releaseFuture.thenRun(session::close);
        return releaseFuture;
    }

    public boolean stop() {
        return stopAsync().join();
    }
}
