package com.ratelimiter.exception;

public class NoRuleFoundException extends RuntimeException {

    public NoRuleFoundException(String message) {
        super(message);
    }
}
