package com.ratelimiter.unit;

import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.exception.NoRuleFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleNoRule returns 422 with correct title")
    void handlesNoRuleFoundException() {
        ProblemDetail problem = handler.handleNoRule(new NoRuleFoundException("No rule for: xyz"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("No Rate Limit Rule Found");
        assertThat(problem.getDetail()).contains("xyz");
    }

    @Test
    @DisplayName("handleUnsupported returns 501 with correct title")
    void handlesUnsupportedOperation() {
        ProblemDetail problem = handler.handleUnsupported(new UnsupportedOperationException("not ready"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED.value());
        assertThat(problem.getTitle()).isEqualTo("Algorithm Not Implemented");
    }

    @Test
    @DisplayName("handleGeneric returns 500 without leaking details")
    void handlesGenericException() {
        ProblemDetail problem = handler.handleGeneric(new RuntimeException("internal db error"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("db error");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("all handlers include a timestamp property")
    void allHandlersIncludeTimestamp() {
        ProblemDetail p1 = handler.handleNoRule(new NoRuleFoundException("x"));
        ProblemDetail p2 = handler.handleUnsupported(new UnsupportedOperationException());
        ProblemDetail p3 = handler.handleGeneric(new RuntimeException());

        assertThat(p1.getProperties()).containsKey("timestamp");
        assertThat(p2.getProperties()).containsKey("timestamp");
        assertThat(p3.getProperties()).containsKey("timestamp");
    }
}
