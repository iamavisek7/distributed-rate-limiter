# Distributed Rate Limiter

A production-style distributed rate limiter built with Java 21, Spring Boot, and Redis.

## Architecture

```
src/main/java/com/ratelimiter/
├── RateLimiterApplication.java
├── algorithm/
│   ├── RateLimitAlgorithm.java       ← strategy interface
│   ├── FixedWindowAlgorithm.java     ← implemented
│   ├── SlidingWindowAlgorithm.java   ← stub (TODO)
│   └── TokenBucketAlgorithm.java     ← stub (TODO)
├── controller/
│   └── RateLimiterController.java    ← POST /api/v1/rate-limit/check
├── service/
│   └── RateLimiterService.java       ← orchestrates rule + algorithm
├── repository/
│   ├── RateLimitRepository.java      ← Redis abstraction
│   └── RedisRateLimitRepository.java ← Lettuce/Spring Data impl
├── config/
│   ├── RedisConfig.java
│   └── RateLimiterProperties.java    ← YAML-bound rule config
├── dto/
│   ├── RateLimitRequest.java
│   └── RateLimitResponse.java
├── model/
│   ├── Algorithm.java
│   ├── RateLimitRule.java
│   └── RateLimitResult.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── NoRuleFoundException.java
└── metrics/
    └── RateLimiterMetrics.java       ← Micrometer Prometheus counters
```

## API

### Check rate limit

```
POST /api/v1/rate-limit/check
Content-Type: application/json

{ "clientId": "my-service", "endpoint": "/api/orders" }
```

**Response — allowed (200)**
```json
{ "allowed": true, "remaining": 94, "retryAfterSeconds": 0, "appliedRule": "default:FIXED_WINDOW:100/60s" }
```

**Response — denied (429)**
```json
{ "allowed": false, "remaining": 0, "retryAfterSeconds": 38, "appliedRule": "default:FIXED_WINDOW:100/60s" }
```

## Running locally

### With Docker Compose
```bash
docker compose up --build
```

Services:
- App:        http://localhost:8080
- Prometheus: http://localhost:9090

### Without Docker
```bash
# Start Redis
docker run -p 6379:6379 redis:7.2.4-alpine

# Run the app
./mvnw spring-boot:run
```

## Testing

```bash
# Unit tests only
./mvnw test -pl . -Dtest="**/unit/**"

# All tests (requires Docker for Testcontainers)
./mvnw verify
```

## Metrics

Prometheus counter: `rate_limiter_requests_total{clientId, algorithm, result}`

Available at: `GET /actuator/prometheus`

## Adding a new algorithm

1. Implement `RateLimitAlgorithm` and annotate with `@Component`
2. Return the correct `Algorithm` enum from `getType()`
3. Spring will auto-register it into the algorithm map in `RateLimiterService`
4. Add the algorithm to a rule in `application.yml`

## Configuration

```yaml
rate-limiter:
  default-algorithm: FIXED_WINDOW
  rules:
    - clientId: default
      algorithm: FIXED_WINDOW
      limitForPeriod: 100
      limitRefreshPeriodSeconds: 60
    - clientId: premium-client
      algorithm: FIXED_WINDOW
      limitForPeriod: 1000
      limitRefreshPeriodSeconds: 60
```
