package com.ratelimiter.algorithm;

import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRepository;
import org.springframework.stereotype.Component;

/**
 * Sliding Window rate limiting algorithm.
 *
 * <p>Tracks each request as a timestamped member in a Redis sorted set.
 * On every call, entries older than the window are pruned, then the new
 * request is added and the set size is compared against the limit.
 *
 * <p>Advantages over Fixed Window:
 * <ul>
 *   <li>No boundary burst — the window is always the last N seconds from now</li>
 *   <li>Smooth, accurate limiting at the cost of slightly higher Redis memory</li>
 * </ul>
 *
 * <p>Redis key pattern: {@code rl:sliding:{clientId}}
 */
@Component
public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    private final RateLimitRepository repository;

    public SlidingWindowAlgorithm(RateLimitRepository repository) {
        this.repository = repository;
    }

    @Override
    public Algorithm getType() {
        return Algorithm.SLIDING_WINDOW;
    }

    @Override
    public RateLimitResult evaluate(String clientId, RateLimitRule rule) {
        long now = System.currentTimeMillis();
        long windowSeconds = rule.limitRefreshPeriodSeconds();
        long windowMs = windowSeconds * 1000L;
        
        long currentWindowStart = now / windowMs;
        long previousWindowStart = currentWindowStart - 1;
        
        String currentHashKey = buildKey(currentWindowStart);
        String previousHashKey = buildKey(previousWindowStart);
        
        // Overlap percentage calculation
        long windowOffsetMs = now % windowMs;
        double previousWindowWeight = 1.0 - ((double) windowOffsetMs / windowMs);

        long previousCount = repository.getHashFieldValue(previousHashKey, clientId);
        long currentCount = repository.incrementHashAndExpire(currentHashKey, clientId, windowSeconds * 2);
        
        long estimatedCount = (long) (previousCount * previousWindowWeight) + currentCount;
        
        long limit = rule.limitForPeriod();
        boolean allowed = estimatedCount <= limit;
        long remaining = Math.max(0, limit - estimatedCount);

        long retryAfter = allowed ? 0 : 1; // Basic retry estimation

        return new RateLimitResult(allowed, remaining, retryAfter, rule.toRuleDescription());
    }

    private String buildKey(long windowStart) {
        return "rl:sliding:%d".formatted(windowStart);
    }
}
