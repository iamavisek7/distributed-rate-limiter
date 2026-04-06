--[[
  Token Bucket Rate Limiter
  =========================
  State is stored as a Redis hash with two fields:
    tokens      – current token count (float, as string)
    last_refill – epoch seconds of the last refill calculation

  On every call this script atomically:
    1. Reads current state  (or bootstraps a full bucket for new keys)
    2. Calculates tokens to add based on elapsed time × refill_rate
    3. Caps tokens at capacity
    4. Consumes one token if available; otherwise returns -1 (denied)
    5. Persists updated state and resets TTL

  KEYS[1]  = rate limit key   (e.g. "rl:token:client-abc")
  ARGV[1]  = capacity         (maximum tokens in the bucket, integer)
  ARGV[2]  = refill_rate      (tokens added per second, float)
  ARGV[3]  = now_seconds      (current epoch seconds, integer)

  Returns:
    >= 0   tokens remaining after consuming one  (request allowed)
      -1   bucket is empty                       (request denied)
--]]

local key         = KEYS[1]
local capacity    = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now         = tonumber(ARGV[3])

-- Read current bucket state; default to a full bucket for brand-new keys
local data        = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens      = tonumber(data[1]) or capacity
local last_refill = tonumber(data[2]) or now

-- Refill: add tokens proportional to elapsed time, cap at capacity
local elapsed = math.max(0, now - last_refill)
tokens = math.min(capacity, tokens + elapsed * refill_rate)

-- TTL keeps the key alive long enough for a full refill cycle + buffer
local ttl = math.ceil(capacity / refill_rate) + 60

if tokens >= 1 then
    -- Consume one token and persist
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, ttl)
    -- Return floor so callers get a stable integer remaining count
    return math.floor(tokens)
else
    -- No token available — still update state so refill time is correct
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, ttl)
    return -1
end
