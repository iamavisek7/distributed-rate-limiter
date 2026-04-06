package com.ratelimiter.unit;

import com.ratelimiter.algorithm.FixedWindowAlgorithm;
import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.dto.RateLimitRequest;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.exception.NoRuleFoundException;
import com.ratelimiter.metrics.RateLimiterMetrics;
import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private FixedWindowAlgorithm fixedWindowAlgorithm;

    @Mock
    private RateLimiterProperties properties;

    @Mock
    private RateLimiterMetrics metrics;

    private RateLimiterService service;

    private static final RateLimitRule DEFAULT_RULE =
            new RateLimitRule("default", Algorithm.FIXED_WINDOW, 100, 60);

    @BeforeEach
    void setUp() {
        when(fixedWindowAlgorithm.getType()).thenReturn(Algorithm.FIXED_WINDOW);
        service = new RateLimiterService(List.of(fixedWindowAlgorithm), properties, metrics);
    }

    @Test
    @DisplayName("should return allowed response when within limit")
    void returnsAllowedResponse() {
        when(properties.getRules()).thenReturn(List.of(DEFAULT_RULE));
        when(fixedWindowAlgorithm.evaluate(anyString(), any()))
                .thenReturn(new RateLimitResult(true, 99, 0, "default:FIXED_WINDOW:100/60s"));

        RateLimitResponse response = service.evaluate(new RateLimitRequest("client-1", "/api/test"));

        assertThat(response.allowed()).isTrue();
        assertThat(response.remaining()).isEqualTo(99);
        verify(metrics).record("client-1", "FIXED_WINDOW", true);
    }

    @Test
    @DisplayName("should return denied response when over limit")
    void returnsDeniedResponse() {
        when(properties.getRules()).thenReturn(List.of(DEFAULT_RULE));
        when(fixedWindowAlgorithm.evaluate(anyString(), any()))
                .thenReturn(new RateLimitResult(false, 0, 42, "default:FIXED_WINDOW:100/60s"));

        RateLimitResponse response = service.evaluate(new RateLimitRequest("client-1", null));

        assertThat(response.allowed()).isFalse();
        assertThat(response.retryAfterSeconds()).isEqualTo(42);
        verify(metrics).record("client-1", "FIXED_WINDOW", false);
    }

    @Test
    @DisplayName("should use client-specific rule when available")
    void usesClientSpecificRule() {
        RateLimitRule clientRule = new RateLimitRule("vip-client", Algorithm.FIXED_WINDOW, 1000, 60);
        when(properties.getRules()).thenReturn(List.of(DEFAULT_RULE, clientRule));
        when(fixedWindowAlgorithm.evaluate(eq("vip-client"), eq(clientRule)))
                .thenReturn(new RateLimitResult(true, 999, 0, clientRule.toRuleDescription()));

        service.evaluate(new RateLimitRequest("vip-client", null));

        verify(fixedWindowAlgorithm).evaluate("vip-client", clientRule);
    }

    @Test
    @DisplayName("should throw NoRuleFoundException when no rule matches and no default")
    void throwsWhenNoRuleFound() {
        when(properties.getRules()).thenReturn(List.of());

        assertThatThrownBy(() -> service.evaluate(new RateLimitRequest("unknown", null)))
                .isInstanceOf(NoRuleFoundException.class)
                .hasMessageContaining("unknown");
    }
}
