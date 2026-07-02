package com.hsbc.fds.syncfacade.grpc;

import com.hsbc.fds.proto.TransactionCheckResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PendingRequestRegistry {

    private final ConcurrentHashMap<String, CompletableFuture<TransactionCheckResponse>> registry =
            new ConcurrentHashMap<>();

    public void register(String requestId, CompletableFuture<TransactionCheckResponse> future) {
        registry.put(requestId, future);
    }

    public CompletableFuture<TransactionCheckResponse> remove(String requestId) {
        return registry.remove(requestId);
    }

    public int pendingCount() {
        return registry.size();
    }
}
