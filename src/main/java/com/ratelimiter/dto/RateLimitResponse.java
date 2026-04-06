package com.ratelimiter.dto;

public record RateLimitResponse(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        String appliedRule
) {
    public static RateLimitResponse allowed(long remaining, String appliedRule) {
        return new RateLimitResponse(true, remaining, 0, appliedRule);
    }

    public static RateLimitResponse denied(long retryAfterSeconds, String appliedRule) {
        return new RateLimitResponse(false, 0, retryAfterSeconds, appliedRule);
    }
}
