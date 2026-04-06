package com.ratelimiter.config;

import com.ratelimiter.model.Algorithm;
import com.ratelimiter.model.RateLimitRule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private Algorithm defaultAlgorithm = Algorithm.FIXED_WINDOW;
    private List<RuleConfig> rules = new ArrayList<>();

    public Algorithm getDefaultAlgorithm() { return defaultAlgorithm; }
    public void setDefaultAlgorithm(Algorithm defaultAlgorithm) { this.defaultAlgorithm = defaultAlgorithm; }

    public List<RateLimitRule> getRules() {
        return rules.stream()
                .map(r -> new RateLimitRule(
                        r.clientId,
                        r.algorithm != null ? r.algorithm : defaultAlgorithm,
                        r.limitForPeriod,
                        r.limitRefreshPeriodSeconds))
                .toList();
    }

    public void setRules(List<RuleConfig> rules) { this.rules = rules; }

    // Mutable inner class needed for Spring Boot property binding
    public static class RuleConfig {
        private String clientId;
        private Algorithm algorithm;
        private long limitForPeriod;
        private long limitRefreshPeriodSeconds;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public Algorithm getAlgorithm() { return algorithm; }
        public void setAlgorithm(Algorithm algorithm) { this.algorithm = algorithm; }

        public long getLimitForPeriod() { return limitForPeriod; }
        public void setLimitForPeriod(long limitForPeriod) { this.limitForPeriod = limitForPeriod; }

        public long getLimitRefreshPeriodSeconds() { return limitRefreshPeriodSeconds; }
        public void setLimitRefreshPeriodSeconds(long s) { this.limitRefreshPeriodSeconds = s; }
    }
}
