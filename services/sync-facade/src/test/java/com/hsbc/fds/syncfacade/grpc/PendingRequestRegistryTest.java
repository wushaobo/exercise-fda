package com.hsbc.fds.syncfacade.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsbc.fds.proto.TransactionCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

class PendingRequestRegistryTest {

    @Test
    void shouldRegisterAndRetrieveFuture() {
        PendingRequestRegistry registry = new PendingRequestRegistry();
        CompletableFuture<TransactionCheckResponse> future = new CompletableFuture<>();

        registry.register("req-1", future);
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
}
