package com.hsbc.fds.rulecheckworker.redis;

import com.hsbc.fds.rulecheckworker.rule.DenylistRule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "fds.redis.enabled", havingValue = "true", matchIfMissing = true)
public class DenylistCache {

    private static final Logger log = LoggerFactory.getLogger(DenylistCache.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final DenylistRule denylistRule;
    private final String denylistKey;

    public DenylistCache(RedisTemplate<String, String> redisTemplate,
                         DenylistRule denylistRule,
                         @Value("${fds.redis.denylist-key:fds:denylist}") String denylistKey) {
        this.redisTemplate = redisTemplate;
        this.denylistRule = denylistRule;
        this.denylistKey = denylistKey;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedRateString = "${fds.denylist.refresh-interval-seconds:60}000")
    public void refresh() {
        try {
            String raw = redisTemplate.opsForValue().get(denylistKey);
            Set<String> accounts;
            if (raw != null && !raw.isBlank()) {
                accounts = Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .collect(Collectors.toCollection(HashSet::new));
            } else {
                accounts = Collections.emptySet();
            }
            denylistRule.updateDenylist(accounts);
            log.info("Denylist refreshed: {} accounts", accounts.size());
        } catch (Exception e) {
            log.error("Failed to refresh denylist from Redis", e);
        }
    }
}
