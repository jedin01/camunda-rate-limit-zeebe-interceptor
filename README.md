# Camunda Rate Limit Zeebe Interceptor

Rate limiting for Camunda 8 / Zeebe gateway requests, implemented in two layers:

- a **gRPC interceptor** for `CreateProcessInstance`
- a **REST filter** for `POST /v1/process-instances` and `POST /v2/process-instances`

Both components apply the same rule: **1 process instance creation per user per 60 seconds**.

## Repository structure

```text
.
├── docker-compose.yml
├── filter/
│   ├── pom.xml
│   └── src/main/java/com/example/RateLimitFilter.java
└── interceptor/
    ├── pom.xml
    └── src/main/java/com/example/RateLimitInterceptor.java
```

## What each module does

### `interceptor`

`/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/interceptor/src/main/java/com/example/RateLimitInterceptor.java`

- Implements `io.grpc.ServerInterceptor`
- Intercepts gRPC calls whose method name contains `CreateProcessInstance`
- Reads the `authorization` metadata header
- Extracts the JWT `sub` claim
- Rejects calls above the limit with `RESOURCE_EXHAUSTED`

### `filter`

`/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/filter/src/main/java/com/example/RateLimitFilter.java`

- Implements `jakarta.servlet.Filter`
- Intercepts REST calls to process instance creation endpoints
- Reads the `Authorization` HTTP header
- Extracts the JWT `sub` claim
- Rejects calls above the limit with HTTP `429`

## How the rate limit works

The current implementation:

- uses the JWT `sub` claim as the user identifier
- stores counters in memory with `ConcurrentHashMap`
- resets the window after `60_000 ms`
- allows only `1` request in each window

This means:

- limits are **per user**
- limits are **per application instance**
- limits are **not persisted**
- restarting the gateway clears counters

## Requirements

- Java 17
- Maven 3.9+
- Docker and Docker Compose (for the local stack)

## Build

Build each artifact separately:

```bash
mvn -f interceptor/pom.xml clean package
mvn -f filter/pom.xml clean package
```

Generated artifacts:

- `interceptor/target/rate-limit-interceptor.jar`
- `filter/target/rate-limit-filter.jar`

## Run locally with Docker Compose

From `/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor`:

```bash
docker compose up --build
```

The compose stack:

- builds both JARs in a Maven container
- starts Zeebe with the custom interceptor and filter mounted into the container
- starts Elasticsearch
- starts Operate on `http://localhost:8081`
- starts Tasklist on `http://localhost:8082`
- disables Camunda security in the sample stack for local setup simplicity

Default exposed ports:

- Zeebe gRPC: `26500`
- Zeebe REST: `8080`
- Operate: `8081`
- Tasklist: `8082`
- Elasticsearch: `9200`

## Runtime configuration

The current runtime wiring is defined in `docker-compose.yml`.

Important environment variables:

| Variable | Purpose |
| --- | --- |
| `ZEEBE_BROKER_GATEWAY_REST_ENABLED=true` | Enables Zeebe REST API |
| `ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_ID=rate-limit` | Registers the interceptor |
| `ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_CLASSNAME=com.example.RateLimitInterceptor` | Interceptor class |
| `ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_JARPATH=/usr/local/zeebe/interceptors/rate-limit-interceptor.jar` | Interceptor JAR path inside container |
| `ZEEBE_BROKER_GATEWAY_FILTERS_0_ID=rate-limit` | Registers the REST filter |
| `ZEEBE_BROKER_GATEWAY_FILTERS_0_CLASSNAME=com.example.RateLimitFilter` | Filter class |
| `ZEEBE_BROKER_GATEWAY_FILTERS_0_JARPATH=/usr/local/camunda/filters/rate-limit-filter.jar` | Filter JAR path inside container |

More detail is available in [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Authentication expectations

Both implementations expect a bearer token that contains a JWT payload with a `sub` claim.

Example payload:

```json
{
  "sub": "user-123"
}
```

Current behavior:

- the token is decoded, not cryptographically verified
- if `sub` is missing or the token is malformed, the request is rejected

This design only makes sense when JWT validation is already handled upstream.
For the provided Docker Compose demo, include an `Authorization` header in your requests even though platform security is disabled.

## Responses when the limit is exceeded

### gRPC

- Status: `RESOURCE_EXHAUSTED`
- Description: `Tente novamente em <n>s`

### REST

- Status: `429 Too Many Requests`
- JSON body with:
  - `error`
  - `message`
  - `retryAfter`

## Quick test flow

1. Start the stack with Docker Compose
2. Send a process creation request with a bearer token containing `sub`
3. Repeat the same request within 60 seconds
4. Expect the second request to be rejected

See [docs/TESTING.md](docs/TESTING.md) for example test steps.

## Current limitations

- Rate limit values are hardcoded in both classes
- State is in memory only
- JWT parsing is manual
- Error messages are currently in Portuguese
- There are no automated tests in the repository yet

## Documentation map

- [docs/CONFIGURATION.md](docs/CONFIGURATION.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/TESTING.md](docs/TESTING.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)
