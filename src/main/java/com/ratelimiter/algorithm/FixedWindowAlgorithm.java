package com.ratelimiter.algorithm;

import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRepository;
import org.springframework.stereotype.Component;

/**
 * Fixed Window rate limiting algorithm.
 *
 * <p>Counts requests in a fixed time window. The counter resets when the window expires.
 * Simple and efficient, but susceptible to burst traffic at window boundaries.
 */
@Component
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private final RateLimitRepository repository;

    public FixedWindowAlgorithm(RateLimitRepository repository) {
        this.repository = repository;
    }

    @Override
    public Algorithm getType() {
        return Algorithm.FIXED_WINDOW;
    }

    @Override
    public RateLimitResult evaluate(String clientId, RateLimitRule rule) {
        long windowStart = System.currentTimeMillis() / 1000 / rule.limitRefreshPeriodSeconds();
        String hashKey = buildKey(windowStart);
        long windowSeconds = rule.limitRefreshPeriodSeconds();
        long limit = rule.limitForPeriod();

        long count = repository.incrementHashAndExpire(hashKey, clientId, windowSeconds * 2);
        long remaining = Math.max(0, limit - count);
        boolean allowed = count <= limit;
        long retryAfter = allowed ? 0 : repository.getTtl(hashKey);

        return new RateLimitResult(allowed, remaining, retryAfter, rule.toRuleDescription());
    }

    private String buildKey(long windowStart) {
        return "rl:fixed:%d".formatted(windowStart);
    }
}
