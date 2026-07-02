package com.hsbc.fds.rulecheckworker;

import com.hsbc.fds.rulecheckworker.redis.ResultPublisher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

    @Bean
    public ResultPublisher resultPublisherMock() {
        return mock(ResultPublisher.class);
    }
}
