package com.hsbc.fds.rulecheckworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RuleCheckWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleCheckWorkerApplication.class, args);
    }
}
