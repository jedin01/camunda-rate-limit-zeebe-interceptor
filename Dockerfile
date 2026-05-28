FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY interceptor /build/interceptor
COPY filter /build/filter

RUN mvn -f /build/interceptor/pom.xml clean package \
    && mvn -f /build/filter/pom.xml clean package

FROM camunda/zeebe:8.5.0

COPY --from=builder /build/interceptor/target/rate-limit-interceptor.jar \
     /usr/local/zeebe/interceptors/rate-limit-interceptor.jar

COPY --from=builder /build/filter/target/rate-limit-filter.jar \
     /usr/local/camunda/filters/rate-limit-filter.jar
