package com.ratelimiter.unit;

import com.ratelimiter.algorithm.TokenBucketAlgorithm;
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
class TokenBucketAlgorithmTest {

    @Mock
    private RateLimitRepository repository;

    private TokenBucketAlgorithm algorithm;

    // 100 tokens, refills 100/60 ≈ 1.67 tokens/sec
    private static final RateLimitRule RULE =
            new RateLimitRule("client-1", Algorithm.TOKEN_BUCKET, 100, 60);

    @BeforeEach
    void setUp() {
        algorithm = new TokenBucketAlgorithm(repository);
    }

    @Test
    @DisplayName("should allow request when tokens are available")
    void allowsRequestWithTokensAvailable() {
        when(repository.tokenBucketConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(49L);  // 49 tokens remaining after consuming 1

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(49L);
        assertThat(result.retryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("should deny request when no tokens available")
    void deniesRequestWithNoTokens() {
        when(repository.tokenBucketConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(-1L);

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("should allow request with exactly one token left")
    void allowsLastToken() {
        when(repository.tokenBucketConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(0L);  // 0 remaining after consuming last token

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isZero();
    }

    @Test
    @DisplayName("should pass correct refill rate to repository")
    void passesCorrectRefillRate() {
        when(repository.tokenBucketConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(99L);

        algorithm.evaluate("client-1", RULE);

        // refillRate = 100 / 60 ≈ 1.666...
        verify(repository).tokenBucketConsume(
                eq("rl:token:client-1"),
                eq(100L),
                doubleThat(rate -> Math.abs(rate - (100.0 / 60)) < 0.001),
                anyLong()
        );
    }

    @Test
    @DisplayName("should calculate retryAfter as ceil(1/refillRate)")
    void calculatesRetryAfterCorrectly() {
        when(repository.tokenBucketConsume(anyString(), anyLong(), anyDouble(), anyLong()))
                .thenReturn(-1L);

        RateLimitResult result = algorithm.evaluate("client-1", RULE);

        // 1 / (100/60) = 0.6 seconds → ceil = 1
        assertThat(result.retryAfterSeconds()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getType should return TOKEN_BUCKET")
    void returnsCorrectType() {
        assertThat(algorithm.getType()).isEqualTo(Algorithm.TOKEN_BUCKET);
    }
}
