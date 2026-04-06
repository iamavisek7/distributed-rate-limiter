package com.ratelimiter.repository;

import java.util.List;

/**
 * Redis operations needed by rate limiting algorithms.
 * Abstracts Redis access to keep algorithms testable.
 */
public interface RateLimitRepository {

    // ── Fixed Window ──────────────────────────────────────────────────────────

    /**
     * Atomically increment a counter key and set its TTL on first write.
     *
     * @param key        Redis key
     * @param ttlSeconds expiry time in seconds (applied only on first increment)
     * @return the counter value after incrementing
     */
    long incrementAndExpire(String key, long ttlSeconds);

    /**
     * Atomically increment a counter inside a Redis hash and set TTL if newly created.
     *
     * @param hashKey    Redis hash key
     * @param hashField  Redis hash field (e.g., clientId)
     * @param ttlSeconds expiry time in seconds
     * @return the counter value after incrementing
     */
    long incrementHashAndExpire(String hashKey, String hashField, long ttlSeconds);

    /**
     * @param hashKey   Redis hash key
     * @param hashField Redis hash field
     * @return the current value, or 0 if it doesn't exist
     */
    long getHashFieldValue(String hashKey, String hashField);

    /**
     * @param key Redis key
     * @return remaining TTL in seconds, or 0 if key doesn't exist
     */
    long getTtl(String key);

    // ── Sliding Window ────────────────────────────────────────────────────────

    // Now uses the Hash-based approach from Fixed Window. No ZSET methods required.


    // ── Token Bucket ──────────────────────────────────────────────────────────

    /**
     * Atomically refill tokens based on elapsed time and attempt to consume one.
     * Uses a Lua script to guarantee atomicity.
     *
     * @param key            Redis hash key
     * @param capacity       maximum number of tokens in the bucket
     * @param refillRate     tokens added per second
     * @param nowSeconds     current epoch seconds
     * @return number of tokens remaining AFTER consuming one, or -1 if denied
     */
    long tokenBucketConsume(String key, long capacity, double refillRate, long nowSeconds);
}
