package com.hsbc.fds.syncfacade.grpc;

import com.hsbc.fds.proto.TransactionCheckResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PendingRequestRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<TransactionCheckResponse>> registry =
            new ConcurrentHashMap<>();

    /**
     * Registers a pending request. Uses putIfAbsent so the first registrant wins.
     *
     * @return true if registered successfully, false if the requestId already exists
     */
    public boolean register(String requestId, CompletableFuture<TransactionCheckResponse> future) {
        return registry.putIfAbsent(requestId, future) == null;
    }

    public CompletableFuture<TransactionCheckResponse> remove(String requestId) {
        return registry.remove(requestId);
    }

    public int pendingCount() {
        return registry.size();
    }
}
