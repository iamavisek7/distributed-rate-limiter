package com.ratelimiter.algorithm;

import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRepository;
import org.springframework.stereotype.Component;

/**
 * Token Bucket rate limiting algorithm.
 *
 * <p>Each client has a bucket of up to {@code limitForPeriod} tokens. Tokens
 * refill at a steady rate of {@code limitForPeriod / limitRefreshPeriodSeconds}
 * tokens per second. Each request consumes exactly one token.
 *
 * <p>Characteristics:
 * <ul>
 *   <li>Allows short bursts up to the full bucket capacity</li>
 *   <li>Enforces a smooth average rate over time</li>
 *   <li>Atomicity guaranteed by a Redis Lua script (no race conditions)</li>
 * </ul>
 *
 * <p>Redis key pattern: {@code rl:token:{clientId}}
 * <p>Redis structure: hash with fields {@code tokens} and {@code last_refill}
 */
@Component
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RateLimitRepository repository;

    public TokenBucketAlgorithm(RateLimitRepository repository) {
        this.repository = repository;
    }

    @Override
    public Algorithm getType() {
        return Algorithm.TOKEN_BUCKET;
    }

    @Override
    public RateLimitResult evaluate(String clientId, RateLimitRule rule) {
        long capacity     = rule.limitForPeriod();
        double refillRate = (double) rule.limitForPeriod() / rule.limitRefreshPeriodSeconds();
        long nowSeconds   = System.currentTimeMillis() / 1000;
        String key        = buildKey(clientId);

        // Returns remaining tokens after consuming one, or -1 if denied
        long remaining = repository.tokenBucketConsume(key, capacity, refillRate, nowSeconds);

        boolean allowed = remaining >= 0;
        long retryAfter = 0;

        if (!allowed) {
            // Earliest time a token will be available = 1/refillRate seconds
            retryAfter = Math.max(1, (long) Math.ceil(1.0 / refillRate));
        }

        return new RateLimitResult(allowed, Math.max(0, remaining), retryAfter, rule.toRuleDescription());
    }

    private String buildKey(String clientId) {
        return "rl:token:%s".formatted(clientId);
    }
}
