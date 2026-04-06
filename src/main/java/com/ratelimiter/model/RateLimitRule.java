package com.ratelimiter.model;

/**
 * Defines a rate limiting rule for a given client.
 *
 * @param clientId                  identifier for the client (e.g. API key, user ID)
 * @param algorithm                 algorithm to use for this rule
 * @param limitForPeriod            maximum number of requests allowed in the window
 * @param limitRefreshPeriodSeconds duration of the window in seconds
 */
public record RateLimitRule(
        String clientId,
        Algorithm algorithm,
        long limitForPeriod,
        long limitRefreshPeriodSeconds
) {
    public String toRuleDescription() {
        return "%s:%s:%d/%ds".formatted(clientId, algorithm, limitForPeriod, limitRefreshPeriodSeconds);
    }
}
