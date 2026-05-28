# Architecture

## Goal

This repository adds request throttling to Camunda 8 / Zeebe process instance creation.

It protects both exposed gateway paths:

- gRPC process creation
- REST process creation

## Components

### 1. gRPC interceptor

File:
`/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/interceptor/src/main/java/com/example/RateLimitInterceptor.java`

Responsibilities:

- inspect inbound gRPC metadata
- target only `CreateProcessInstance`
- extract `sub` from the bearer token
- keep an in-memory counter per user
- reject excess calls with gRPC `RESOURCE_EXHAUSTED`

### 2. REST filter

File:
`/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/filter/src/main/java/com/example/RateLimitFilter.java`

Responsibilities:

- inspect inbound HTTP requests
- target `POST /v1/process-instances`
- target `POST /v2/process-instances`
- extract `sub` from the bearer token
- keep an in-memory counter per user
- reject excess calls with HTTP `429`

## Request flow

### gRPC

1. Client sends `CreateProcessInstance`
2. `RateLimitInterceptor` checks the method name
3. Interceptor extracts the user id from JWT `sub`
4. Counter is evaluated for the current 60-second window
5. Request is accepted or rejected

### REST

1. Client sends `POST /v1/process-instances` or `POST /v2/process-instances`
2. `RateLimitFilter` checks method and URI
3. Filter extracts the user id from JWT `sub`
4. Counter is evaluated for the current 60-second window
5. Request is accepted or rejected

## State model

Both components use `ConcurrentHashMap` keyed by user id.

Implications:

- thread-safe access inside one JVM
- no shared limit across multiple nodes
- counters are cleared on restart

## Deployment model

The repository ships a Docker Compose example where:

- a Maven container builds both JARs
- Zeebe mounts the generated artifacts
- Zeebe registers the interceptor and filter through environment variables

## Security assumptions

The code decodes JWT payloads and reads `sub`, but it does not validate signatures.

Use this only when:

- identity is validated before the request reaches the gateway, or
- the environment is strictly controlled for demonstration purposes

## Operational limits

The current design is best suited for:

- local environments
- demos
- small single-instance deployments

For production-scale use, consider:

- externalized configuration
- shared distributed counters
- proper JWT validation
- automated tests for edge cases
