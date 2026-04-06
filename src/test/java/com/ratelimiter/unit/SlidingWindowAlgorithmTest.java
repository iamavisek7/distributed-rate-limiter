package com.ratelimiter.unit;

import com.ratelimiter.algorithm.SlidingWindowAlgorithm;
import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowAlgorithmTest {

    @Mock
    private RateLimitRepository repository;

    private SlidingWindowAlgorithm algorithm;

    private static final RateLimitRule RULE =
            new RateLimitRule("client-1", Algorithm.SLIDING_WINDOW, 10, 60);

    @BeforeEach
    void setUp() {
        algorithm = new SlidingWindowAlgorithm(repository);
    }

    @Test
    @DisplayName("should allow request when mathematically estimated count is within limit")
    void allowsRequestWithinLimit() {
        when(repository.getHashFieldValue(anyString(), anyString())).thenReturn(5L);
        when(repository.incrementHashAndExpire(anyString(), anyString(), anyLong())).thenReturn(2L);

        // Previous = 5. Overlap weight varies, but max is 5. Current = 2. Max possible count = 7. Limit = 10.
        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isTrue();
        // Remaining should be dynamically calculated, just verifying it was allowed.
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("should deny request when estimated count exceeds limit")
    void deniesRequestOverLimit() {
        when(repository.getHashFieldValue(anyString(), anyString())).thenReturn(11L);
        when(repository.incrementHashAndExpire(anyString(), anyString(), anyLong())).thenReturn(20L);

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("should pass correct hash keys")
    void passesCorrectHashKeys() {
        when(repository.getHashFieldValue(anyString(), anyString())).thenReturn(1L);
        when(repository.incrementHashAndExpire(anyString(), anyString(), anyLong())).thenReturn(1L);

        algorithm.evaluate("client-1", RULE);

        long now = System.currentTimeMillis();
        long windowStart = now / 60_000L;

        verify(repository).getHashFieldValue(
                eq("rl:sliding:" + (windowStart - 1)),
                eq("client-1")
        );
        
        verify(repository).incrementHashAndExpire(
                eq("rl:sliding:" + windowStart),
                eq("client-1"),
                eq(120L)
        );
    }

    @Test
    @DisplayName("getType should return SLIDING_WINDOW")
    void returnsCorrectType() {
        assertThat(algorithm.getType()).isEqualTo(Algorithm.SLIDING_WINDOW);
    }
}
