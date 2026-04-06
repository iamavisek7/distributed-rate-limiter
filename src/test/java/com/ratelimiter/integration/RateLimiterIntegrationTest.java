package com.ratelimiter.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.dto.RateLimitRequest;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static final RedisContainer REDIS = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.2.4"));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void flushRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private void postCheck(String clientId) throws Exception {
        mockMvc.perform(post("/api/v1/rate-limit/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RateLimitRequest(clientId, "/test"))));
    }

    // ── Fixed Window ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fixed Window")
    class FixedWindow {

        @Test
        @DisplayName("returns 200 with correct fields on first request")
        void firstRequestAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RateLimitRequest("default", "/api"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true))
                    .andExpect(jsonPath("$.remaining").isNumber())
                    .andExpect(jsonPath("$.retryAfterSeconds").value(0))
                    .andExpect(jsonPath("$.appliedRule").isString());
        }

        @Test
        @DisplayName("remaining decrements with each request")
        void remainingDecrementsCorrectly() throws Exception {
            // First request → remaining = 99
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RateLimitRequest("default", null))))
                    .andExpect(jsonPath("$.remaining").value(99));

            // Second request → remaining = 98
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RateLimitRequest("default", null))))
                    .andExpect(jsonPath("$.remaining").value(98));
        }

        @Test
        @DisplayName("returns 429 after exhausting the limit")
        void returns429AfterExhaustingLimit() throws Exception {
            for (int i = 0; i < 100; i++) {
                postCheck("default");
            }
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RateLimitRequest("default", null))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.allowed").value(false))
                    .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
        }

        @Test
        @DisplayName("different clientIds have independent counters")
        void independentCountersPerClient() throws Exception {
            // Exhaust client-a
            for (int i = 0; i < 100; i++) postCheck("default");

            // client-b (falls back to default rule) should be unaffected... wait,
            // both would share the same "default" rule but different Redis keys.
            // Use a distinct clientId that also maps to the default rule.
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RateLimitRequest("other-client", null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }
    }

    // ── Sliding Window ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sliding Window")
    class SlidingWindow {

        @Test
        @DisplayName("returns 200 on first request")
        void firstRequestAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RateLimitRequest("sliding-client", "/api"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        @Test
        @DisplayName("returns 429 after exhausting the sliding window limit")
        void returns429AfterExhaustingLimit() throws Exception {
            for (int i = 0; i < 100; i++) postCheck("sliding-client");

            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RateLimitRequest("sliding-client", null))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.allowed").value(false));
        }
    }

    // ── Token Bucket ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Token Bucket")
    class TokenBucket {

        @Test
        @DisplayName("returns 200 on first request with full bucket")
        void firstRequestAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RateLimitRequest("token-client", "/api"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        @Test
        @DisplayName("returns 429 after draining the bucket")
        void returns429AfterDrainingBucket() throws Exception {
            for (int i = 0; i < 100; i++) postCheck("token-client");

            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RateLimitRequest("token-client", null))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.allowed").value(false))
                    .andExpect(jsonPath("$.retryAfterSeconds").value(1));
        }
    }

    // ── Validation & error handling ───────────────────────────────────────────

    @Nested
    @DisplayName("Validation and error handling")
    class Validation {

        @Test
        @DisplayName("returns 400 when clientId is blank")
        void rejectsMissingClientId() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clientId\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }

        @Test
        @DisplayName("returns 400 when body is empty JSON object")
        void rejectsEmptyBody() throws Exception {
            mockMvc.perform(post("/api/v1/rate-limit/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /status/{clientId} returns 200 with rule metadata")
        void statusEndpointReturnsMetadata() throws Exception {
            mockMvc.perform(get("/api/v1/rate-limit/status/default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.appliedRule").isString());
        }
    }
}

