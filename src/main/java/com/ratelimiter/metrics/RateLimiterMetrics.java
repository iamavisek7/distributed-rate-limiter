package com.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Records rate limiter decisions as Prometheus counters.
 *
 * Exposed metrics:
 *   rate_limiter_requests_total{clientId, algorithm, result="allowed|denied"}
 */
@Component
public class RateLimiterMetrics {

    private static final String METRIC_NAME = "rate_limiter_requests_total";
    private static final String TAG_CLIENT   = "clientId";
    private static final String TAG_ALGO     = "algorithm";
    private static final String TAG_RESULT   = "result";

    private final MeterRegistry meterRegistry;

    public RateLimiterMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String clientId, String algorithm, boolean allowed) {
        counter(clientId, algorithm, allowed ? "allowed" : "denied").increment();
    }

    private Counter counter(String clientId, String algorithm, String result) {
        return Counter.builder(METRIC_NAME)
                .description("Total rate limiter decisions")
                .tag(TAG_CLIENT, clientId)
                .tag(TAG_ALGO, algorithm)
                .tag(TAG_RESULT, result)
                .register(meterRegistry);
    }
}
