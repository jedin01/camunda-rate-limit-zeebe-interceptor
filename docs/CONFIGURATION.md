# Configuration

## Current defaults in code

Both modules use the same hardcoded rate-limit policy:

| Setting | Value |
| --- | --- |
| Maximum requests per window | `1` |
| Window size | `60_000 ms` |
| User key | JWT `sub` claim |

Source files:

- `/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/interceptor/src/main/java/com/example/RateLimitInterceptor.java`
- `/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/filter/src/main/java/com/example/RateLimitFilter.java`

## Docker Compose wiring

The local environment is configured in:

- `/tmp/workspace/jedin01/camunda-rate-limit-zeebe-interceptor/docker-compose.yml`

### Build stage

The `build-artifacts` service runs:

```bash
mvn -f interceptor/pom.xml clean package
mvn -f filter/pom.xml clean package
```

### Zeebe runtime settings

| Variable | Value | Meaning |
| --- | --- | --- |
| `ZEEBE_BROKER_GATEWAY_REST_ENABLED` | `true` | Turns on REST API support |
| `ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_ID` | `rate-limit` | Interceptor identifier |
| `ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_CLASSNAME` | `com.example.RateLimitInterceptor` | Interceptor implementation |
| `ZEEBE_BROKER_GATEWAY_INTERCEPTORS_0_JARPATH` | `/usr/local/zeebe/interceptors/rate-limit-interceptor.jar` | Mounted interceptor JAR |
| `ZEEBE_BROKER_GATEWAY_FILTERS_0_ID` | `rate-limit` | Filter identifier |
| `ZEEBE_BROKER_GATEWAY_FILTERS_0_CLASSNAME` | `com.example.RateLimitFilter` | Filter implementation |
| `ZEEBE_BROKER_GATEWAY_FILTERS_0_JARPATH` | `/usr/local/camunda/filters/rate-limit-filter.jar` | Mounted filter JAR |

## Authentication input

The components expect:

- an `Authorization` or `authorization` bearer token header
- a JWT with a decodable payload
- a `sub` claim

If that data is missing or invalid:

- gRPC requests return `UNAUTHENTICATED`
- REST requests return `401`

## Customization guidance

If you need to adapt this project, the first configuration candidates are:

- requests per window
- window duration
- targeted endpoint list
- error message language
- token parsing strategy

## Production notes

The current implementation does not yet provide:

- environment-based configuration
- distributed counters
- persistent state
- JWT signature verification

Treat the current setup as a simple baseline implementation.
