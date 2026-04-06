package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimitAlgorithm;
import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.dto.RateLimitRequest;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.exception.NoRuleFoundException;
import com.ratelimiter.metrics.RateLimiterMetrics;
import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String DEFAULT_CLIENT = "default";

    private final Map<Algorithm, RateLimitAlgorithm> algorithms;
    private final RateLimiterProperties properties;
    private final RateLimiterMetrics metrics;

    public RateLimiterService(
            List<RateLimitAlgorithm> algorithmList,
            RateLimiterProperties properties,
            RateLimiterMetrics metrics) {
        this.algorithms = algorithmList.stream()
                .collect(Collectors.toMap(RateLimitAlgorithm::getType, Function.identity()));
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * Evaluate a rate limit request — consumes one token / increments the counter.
     */
    public RateLimitResponse evaluate(RateLimitRequest request) {
        RateLimitRule rule = resolveRule(request.clientId());
        RateLimitAlgorithm algorithm = algorithms.get(rule.algorithm());

        log.debug("Evaluating rate limit for clientId={} using algorithm={}", request.clientId(), rule.algorithm());

        RateLimitResult result = algorithm.evaluate(request.clientId(), rule);
        metrics.record(request.clientId(), rule.algorithm().name(), result.allowed());

        return toResponse(result);
    }

    /**
     * Peek at the current status for a client WITHOUT consuming a token.
     * Only meaningful for Fixed Window (reads TTL + current count).
     * For other algorithms it delegates to evaluate but logs it as a peek.
     */
    public RateLimitResponse peek(String clientId) {
        RateLimitRule rule = resolveRule(clientId);
        log.debug("Peeking rate limit status for clientId={}", clientId);
        // For a true non-destructive peek, Fixed Window and Sliding Window
        // would need dedicated read methods. For now we surface the rule metadata.
        return new RateLimitResponse(true, rule.limitForPeriod(), 0, rule.toRuleDescription());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RateLimitRule resolveRule(String clientId) {
        return properties.getRules().stream()
                .filter(r -> r.clientId().equals(clientId))
                .findFirst()
                .or(() -> properties.getRules().stream()
                        .filter(r -> r.clientId().equals(DEFAULT_CLIENT))
                        .findFirst())
                .orElseThrow(() -> new NoRuleFoundException(
                        "No rate limit rule found for clientId: " + clientId));
    }

    private RateLimitResponse toResponse(RateLimitResult result) {
        return new RateLimitResponse(
                result.allowed(),
                result.remaining(),
                result.retryAfterSeconds(),
                result.appliedRule());
    }
}
