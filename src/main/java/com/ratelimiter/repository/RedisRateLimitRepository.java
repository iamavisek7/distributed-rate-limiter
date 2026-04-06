package com.ratelimiter.repository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of {@link RateLimitRepository}.
 *
 * <p>Fixed Window uses a plain INCR + EXPIRE.
 *
 * <p>Sliding Window and Token Bucket use Lua scripts loaded from
 * {@code src/main/resources/scripts/} to guarantee atomicity across
 * the read-modify-write cycle. Redis executes Lua scripts as a single
 * atomic command — no locks, no race conditions, even under distributed load.
 *
 * <p>Spring caches the compiled script SHA on first {@code EVALSHA} call
 * and falls back to {@code EVAL} automatically if the script is evicted.
 */
@Repository
public class RedisRateLimitRepository implements RateLimitRepository {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

    public RedisRateLimitRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate      = redisTemplate;
        this.tokenBucketScript   = loadScript("scripts/token_bucket.lua");
    }

    // ── Fixed Window ──────────────────────────────────────────────────────────

    @Override
    public long incrementAndExpire(String key, long ttlSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Redis INCR returned null for key: " + key);
        }
        // Set TTL only on the first write; subsequent increments keep the existing window
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return count;
    }

    @Override
    public long getTtl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    // ── Hash Operations (Fixed & Sliding Window) ──────────────────────────────

    @Override
    public long incrementHashAndExpire(String hashKey, String hashField, long ttlSeconds) {
        Long count = redisTemplate.opsForHash().increment(hashKey, hashField, 1);
        if (count == 1) { 
            // Setting TTL on the first time this field is incremented.
            // If the hash already exists from another user, we just refresh the TTL which is safe.
            redisTemplate.expire(hashKey, Duration.ofSeconds(ttlSeconds));
        }
        return count;
    }

    @Override
    public long getHashFieldValue(String hashKey, String hashField) {
        Object val = redisTemplate.opsForHash().get(hashKey, hashField);
        if (val == null) {
            return 0L;
        }
        return Long.parseLong(val.toString());
    }

    // ── Token Bucket ──────────────────────────────────────────────────────────

    /**
     * Delegates to {@code scripts/token_bucket.lua}.
     *
     * <p>The Lua script atomically:
     * <ol>
     *   <li>Reads {@code tokens} and {@code last_refill} from a Redis hash</li>
     *   <li>Refills tokens proportional to elapsed time × {@code refillRate}</li>
     *   <li>Caps tokens at {@code capacity}</li>
     *   <li>Consumes one token if available (returns remaining count)</li>
     *   <li>Returns {@code -1} if the bucket is empty (request denied)</li>
     * </ol>
     */
    @Override
    public long tokenBucketConsume(String key, long capacity, double refillRate, long nowSeconds) {
        Long result = redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(nowSeconds));
        if (result == null) {
            throw new IllegalStateException("token_bucket.lua returned null for key: " + key);
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static DefaultRedisScript<Long> loadScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpathLocation)));
        script.setResultType(Long.class);
        return script;
    }
}
