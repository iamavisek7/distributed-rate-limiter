package com.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;

public record RateLimitRequest(
        @NotBlank(message = "clientId must not be blank")
        String clientId,

        String endpoint
) {}
