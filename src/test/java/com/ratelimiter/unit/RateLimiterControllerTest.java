package com.ratelimiter.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.controller.RateLimiterController;
import com.ratelimiter.dto.RateLimitRequest;
import com.ratelimiter.dto.RateLimitResponse;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.exception.NoRuleFoundException;
import com.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {RateLimiterController.class, GlobalExceptionHandler.class})
class RateLimiterControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RateLimiterService rateLimiterService;

    @Test
    @DisplayName("POST /check returns 200 when allowed")
    void returns200WhenAllowed() throws Exception {
        when(rateLimiterService.evaluate(any()))
                .thenReturn(new RateLimitResponse(true, 99, 0, "default:FIXED_WINDOW:100/60s"));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RateLimitRequest("client-1", "/api"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(99))
                .andExpect(jsonPath("$.retryAfterSeconds").value(0))
                .andExpect(jsonPath("$.appliedRule").value("default:FIXED_WINDOW:100/60s"));
    }

    @Test
    @DisplayName("POST /check returns 429 when denied")
    void returns429WhenDenied() throws Exception {
        when(rateLimiterService.evaluate(any()))
                .thenReturn(new RateLimitResponse(false, 0, 30, "default:FIXED_WINDOW:100/60s"));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RateLimitRequest("client-1", null))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
    }

    @Test
    @DisplayName("POST /check returns 400 when clientId is blank")
    void returns400WhenClientIdBlank() throws Exception {
        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /check returns 400 when body is missing")
    void returns400WhenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /check returns 422 when no rule found")
    void returns422WhenNoRuleFound() throws Exception {
        when(rateLimiterService.evaluate(any()))
                .thenThrow(new NoRuleFoundException("No rule for clientId: unknown"));

        mockMvc.perform(post("/api/v1/rate-limit/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RateLimitRequest("unknown", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("No Rate Limit Rule Found"));
    }

    @Test
    @DisplayName("GET /status/{clientId} returns 200 with rule info")
    void returnsStatusForClient() throws Exception {
        when(rateLimiterService.peek("client-1"))
                .thenReturn(new RateLimitResponse(true, 100, 0, "default:FIXED_WINDOW:100/60s"));

        mockMvc.perform(get("/api/v1/rate-limit/status/client-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.appliedRule").isString());
    }
}
