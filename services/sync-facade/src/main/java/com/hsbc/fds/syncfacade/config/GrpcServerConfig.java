package com.hsbc.fds.syncfacade.config;

import com.hsbc.fds.syncfacade.grpc.FraudDetectionServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "fds.grpc.enabled", havingValue = "true", matchIfMissing = true)
public class GrpcServerConfig {

    private final FraudDetectionServiceImpl fraudDetectionService;
    private final int port;

    public GrpcServerConfig(FraudDetectionServiceImpl fraudDetectionService,
                            @Value("${fds.grpc.port:9090}") int port) {
        this.fraudDetectionService = fraudDetectionService;
        this.port = port;
    }

    @Bean(destroyMethod = "shutdown")
    public Server grpcServer() throws IOException {
        return ServerBuilder.forPort(port)
                .addService(fraudDetectionService)
                .build()
                .start();
    }

    @Bean
    public org.springframework.boot.ApplicationRunner grpcServerRunner(Server server) {
        return args -> server.awaitTermination();
    }
}
