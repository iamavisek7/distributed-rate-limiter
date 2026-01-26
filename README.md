# distributed-rate-limiter(Token Bucket)

A production-ready distributed rate limiting service built using Java, Spring Boot, Redis, and Lua, designed to enforce API rate limits consistently across multiple application instances.

🚀 Features

- Distributed rate limiting using Redis as a centralized store

- Token Bucket algorithm (supports burst traffic)

- Atomic operations using Redis Lua scripting (concurrency-safe)

- Configurable rate limit policies (per route)

- HTTP 429 responses with standard rate-limit headers

- Low-latency enforcement suitable for high-throughput APIs

- Metrics-ready design (Micrometer compatible)

- Fail-safe behavior for Redis unavailability
