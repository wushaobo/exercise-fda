package com.hsbc.fds.rulecheckworker.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hsbc.fds.rulecheckworker.rule.DenylistRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DenylistCacheTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldLoadCommaSeparatedAccountsFromRedis() {
        DenylistRule denylistRule = new DenylistRule();
        DenylistCache cache = new DenylistCache(redisTemplate, denylistRule, "fds:denylist");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fds:denylist")).thenReturn("a,b,c");

        cache.refresh();

        assertThat(denylistRule.getDenylist()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void shouldHandleEmptyRedisValue() {
        DenylistRule denylistRule = new DenylistRule();
        denylistRule.updateDenylist(java.util.Set.of("existing"));
        DenylistCache cache = new DenylistCache(redisTemplate, denylistRule, "fds:denylist");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fds:denylist")).thenReturn(null);

        cache.refresh();

        assertThat(denylistRule.getDenylist()).isEmpty();
    }

    @Test
    void shouldTrimWhitespaceAroundAccountIds() {
        DenylistRule denylistRule = new DenylistRule();
        DenylistCache cache = new DenylistCache(redisTemplate, denylistRule, "fds:denylist");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fds:denylist")).thenReturn("acc1, acc2 , acc3");

        cache.refresh();

        assertThat(denylistRule.getDenylist()).containsExactlyInAnyOrder("acc1", "acc2", "acc3");
    }

    @Test
    void shouldNormalizeToLowercase() {
        DenylistRule denylistRule = new DenylistRule();
        DenylistCache cache = new DenylistCache(redisTemplate, denylistRule, "fds:denylist");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fds:denylist")).thenReturn("ACC1, acc2, Acc3");

        cache.refresh();

        assertThat(denylistRule.getDenylist()).containsExactlyInAnyOrder("acc1", "acc2", "acc3");
    }

    @Test
    void shouldHandleBlankRedisValue() {
        DenylistRule denylistRule = new DenylistRule();
        DenylistCache cache = new DenylistCache(redisTemplate, denylistRule, "fds:denylist");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fds:denylist")).thenReturn("  ");

        cache.refresh();

        assertThat(denylistRule.getDenylist()).isEmpty();
    }
}
