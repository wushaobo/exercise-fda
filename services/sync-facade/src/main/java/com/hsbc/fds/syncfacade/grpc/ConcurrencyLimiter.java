package com.hsbc.fds.syncfacade.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class ConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiter.class);

    private final Semaphore semaphore;
    private final long acquireTimeoutMillis;

    public ConcurrencyLimiter(
            @Value("${fds.concurrency.max-in-flight:1000}") int maxInFlight,
            @Value("${fds.concurrency.acquire-timeout-millis:50}") long acquireTimeoutMillis) {
        this.semaphore = new Semaphore(maxInFlight, true);
        this.acquireTimeoutMillis = acquireTimeoutMillis;
    }

    public boolean tryAcquire() {
        try {
            boolean acquired = semaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Concurrency limit reached, permits={}", semaphore.availablePermits());
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        semaphore.release();
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
