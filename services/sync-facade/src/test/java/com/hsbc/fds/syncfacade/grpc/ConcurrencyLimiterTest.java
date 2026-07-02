package com.hsbc.fds.syncfacade.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class ConcurrencyLimiterTest {

    @Test
    void shouldAcquireAndReleaseSuccessfully() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(2, 100);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse(); // third one blocked

        limiter.release();
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    void shouldTrackAvailablePermits() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(5, 100);

        assertThat(limiter.availablePermits()).isEqualTo(5);

        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.availablePermits()).isEqualTo(3);
    }

    @Test
    void shouldHandleConcurrentAccess() throws Exception {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(2, 10);
        int threadCount = 6;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger acquired = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                if (limiter.tryAcquire()) {
                    acquired.incrementAndGet();
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                    limiter.release();
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.close();

        assertThat(acquired.get()).isEqualTo(2);
    }

}
