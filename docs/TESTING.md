# Testing Guide

## Current state

There are no automated tests in this repository today.

Validation currently relies on:

- Maven packaging for each module
- manual runtime checks against the local Docker Compose stack

## Build validation

Run:

```bash
mvn -f interceptor/pom.xml clean package
mvn -f filter/pom.xml clean package
```

If your environment blocks Maven Central access, packaging will fail before compilation.

## Manual behavior test

### 1. Start the local stack

```bash
docker compose up --build
```

### 2. Use a bearer token with a `sub` claim

Because the current implementation only decodes the JWT payload, a demo token must simply contain a valid base64url payload with `sub`.

Example payload:

```json
{"sub":"user-123"}
```

### 3. Send a first REST process creation request

Use the Zeebe REST endpoint exposed on port `8080`.

Expected result:

- first request is accepted

### 4. Repeat the same request within 60 seconds

Expected result:

- response status `429`
- response body includes `retryAfter`

### 5. Test gRPC behavior

Send two `CreateProcessInstance` requests for the same JWT subject within the same minute.

Expected result:

- first request is accepted
- second request returns `RESOURCE_EXHAUSTED`

## Recommended future automated tests

Useful additions:

- JWT parsing tests
- malformed token tests
- missing `sub` tests
- window reset tests
- repeated request limit tests
- gRPC interceptor unit tests
- servlet filter unit tests
