package com.ratelimiter.model;

/**
 * Result produced by a rate limiting algorithm after evaluating a request.
 */
public record RateLimitResult(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        String appliedRule
) {}
