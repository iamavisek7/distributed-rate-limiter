package com.ratelimiter.algorithm;

import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;

/**
 * Strategy interface for rate limiting algorithms.
 * Each implementation encapsulates one algorithm and operates on Redis state.
 */
public interface RateLimitAlgorithm {

    /**
     * @return the algorithm type this implementation handles
     */
    Algorithm getType();

    /**
     * Evaluate whether a request from the given client is allowed under the rule.
     *
     * @param clientId unique identifier of the calling client
     * @param rule     the rule to apply
     * @return result containing allow/deny decision and metadata
     */
    RateLimitResult evaluate(String clientId, RateLimitRule rule);
}
