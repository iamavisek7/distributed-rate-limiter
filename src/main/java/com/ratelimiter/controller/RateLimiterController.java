package com.ratelimiter.controller;

import com.ratelimiter.dto.RateLimitRequest;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rate-limit")
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    public RateLimiterController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Consume one token / count one request for the given client.
     * Returns HTTP 200 when allowed, HTTP 429 when the limit is exceeded.
     */
    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> check(@Valid @RequestBody RateLimitRequest request) {
        RateLimitResponse response = rateLimiterService.evaluate(request);
        HttpStatus status = response.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Peek at the current rate limit status for a client WITHOUT consuming a token.
     * Useful for dashboards and health checks.
     */
    @GetMapping("/status/{clientId}")
    public ResponseEntity<RateLimitResponse> status(@PathVariable String clientId) {
        RateLimitResponse response = rateLimiterService.peek(clientId);
        return ResponseEntity.ok(response);
    }
}
