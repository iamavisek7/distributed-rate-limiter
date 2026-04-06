package com.ratelimiter.unit;

import com.ratelimiter.algorithm.FixedWindowAlgorithm;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixedWindowAlgorithmTest {

    @Mock
    private RateLimitRepository repository;

    private FixedWindowAlgorithm algorithm;

    private static final RateLimitRule RULE = new RateLimitRule("client-1", Algorithm.FIXED_WINDOW, 10, 60);

    @BeforeEach
    void setUp() {
        algorithm = new FixedWindowAlgorithm(repository);
    }

    @Test
    @DisplayName("should allow request when count is within limit")
    void allowsRequestWithinLimit() {
        when(repository.incrementHashAndExpire(anyString(), anyString(), anyLong())).thenReturn(5L);

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(5L);
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("should deny request when count exceeds limit")
    void deniesRequestOverLimit() {
        when(repository.incrementHashAndExpire(anyString(), anyString(), anyLong())).thenReturn(11L);
        when(repository.getTtl(anyString())).thenReturn(30L);

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(30L);
    }

    @Test
    @DisplayName("should allow request exactly at the limit")
    void allowsRequestAtExactLimit() {
        when(repository.incrementHashAndExpire(anyString(), anyString(), anyLong())).thenReturn(10L);

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isZero();
    }

    @Test
    @DisplayName("getType should return FIXED_WINDOW")
    void returnsCorrectAlgorithmType() {
        assertThat(algorithm.getType()).isEqualTo(Algorithm.FIXED_WINDOW);
    }
}
