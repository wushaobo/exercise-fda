package com.hsbc.fds.syncfacade.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.hsbc.fds.proto.TransactionCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class PendingRequestRegistryTest {

    @Test
    void shouldRegisterAndRetrieveFuture() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();

        boolean registered = registry.register("req-1", future);
        assertThat(registered).isTrue();

        CompletableFuture<TransactionCheckResponse> retrieved = registry.remove("req-1");
        assertThat(retrieved).isSameAs(future);
    }

    @Test
    void shouldReturnNullForUnknownRequestId() {
        PendingRequestRegistry registry = new PendingRequestRegistry();

        assertThat(registry.remove("unknown")).isNull();
    }

    @Test
    void shouldTrackPendingCount() {
        PendingRequestRegistry registry = new PendingRequestRegistry();

        registry.register("req-1", new CompletableFuture<>());
        registry.register("req-2", new CompletableFuture<>());

        assertThat(registry.pendingCount()).isEqualTo(2);

        registry.remove("req-1");
        assertThat(registry.pendingCount()).isEqualTo(1);
    }

    @Test
    void shouldRemoveEntryAfterCompletion() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();
        registry.register("req-1", future);

        future.complete(TransactionCheckResponse.getDefaultInstance());

        registry.remove("req-1");
        assertThat(registry.pendingCount()).isEqualTo(0);
    }

    // -- duplicate requestId tests --

    @Test
    void shouldReturnFalseWhenRegisteringDuplicateKey() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        CompletableFuture<TransactionCheckResponse> f1 = new CompletableFuture<>();
        CompletableFuture<TransactionCheckResponse> f2 = new CompletableFuture<>();

        assertThat(registry.register("req-1", f1)).isTrue();
        assertThat(registry.register("req-1", f2)).isFalse();
    }

    @Test
    void shouldPreserveFirstFutureOnDuplicateKey() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        CompletableFuture<TransactionCheckResponse> f1 = new CompletableFuture<>();
        CompletableFuture<TransactionCheckResponse> f2 = new CompletableFuture<>();

        registry.register("req-1", f1);
        registry.register("req-1", f2); // should be rejected

        CompletableFuture<TransactionCheckResponse> retrieved = registry.remove("req-1");
        assertThat(retrieved).isSameAs(f1); // first registrant wins
        assertThat(retrieved).isNotSameAs(f2);
    }

    @Test
    void shouldNotChangeCountOnDuplicateKey() {
        PendingRequestRegistry registry = new PendingRequestRegistry();

        registry.register("req-1", new CompletableFuture<>());
        assertThat(registry.pendingCount()).isEqualTo(1);

        registry.register("req-1", new CompletableFuture<>());
        assertThat(registry.pendingCount()).isEqualTo(1); // unchanged
    }

    // -- double remove --

    @Test
    void shouldReturnNullOnDoubleRemove() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        registry.register("req-1", new CompletableFuture<>());

        assertThat(registry.remove("req-1")).isNotNull();
        assertThat(registry.remove("req-1")).isNull();
    }

    // -- null rejection --

    @Test
    void shouldRejectNullKey() {
        PendingRequestRegistry registry = new PendingRequestRegistry();

        assertThatNullPointerException()
                .isThrownBy(() -> registry.register(null, new CompletableFuture<>()));
    }

    @Test
    void shouldRejectNullValue() {
        PendingRequestRegistry registry = new PendingRequestRegistry();

        assertThatNullPointerException()
                .isThrownBy(() -> registry.register("req-1", null));
    }

    // -- concurrent access tests --

    @Test
    void shouldHandleConcurrentRegistersWithDifferentKeys() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String id = "req-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean ok = registry.register(id, new CompletableFuture<>());
                    if (ok) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(registry.pendingCount()).isEqualTo(threadCount);

        // verify all can be retrieved
        for (int i = 0; i < threadCount; i++) {
            assertThat(registry.remove("req-" + i)).isNotNull();
        }
    }

    @Test
    void shouldAllowOnlyOneWinnerForConcurrentSameKey() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<CompletableFuture<TransactionCheckResponse>> allFutures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            CompletableFuture<TransactionCheckResponse> f = new CompletableFuture<>();
            allFutures.add(f);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean ok = registry.register("same-key", f);
                    if (ok) successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one thread should succeed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(registry.pendingCount()).isEqualTo(1);
    }

    @Test
    void shouldHandleConcurrentRegisterAndRemove() throws Exception {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        int count = 100;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(count * 2);

        // Pre-register half
        for (int i = 0; i < count; i++) {
            registry.register("pre-" + i, new CompletableFuture<>());
        }

        // Half threads register new, half threads remove pre-registered
        for (int i = 0; i < count; i++) {
            final int idx = i;
            executor.submit(() -> {
                try { startLatch.await(); } catch (InterruptedException e) { return; }
                registry.register("new-" + idx, new CompletableFuture<>());
                doneLatch.countDown();
            });
            executor.submit(() -> {
                try { startLatch.await(); } catch (InterruptedException e) { return; }
                registry.remove("pre-" + idx);
                doneLatch.countDown();
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // All pre-registered should be removed, all new should be present
        assertThat(registry.pendingCount()).isEqualTo(count);
        for (int i = 0; i < count; i++) {
            assertThat(registry.remove("pre-" + i)).isNull();
            assertThat(registry.remove("new-" + i)).isNotNull();
        }
    }
}
