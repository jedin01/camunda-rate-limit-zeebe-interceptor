FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY interceptor /build/interceptor
RUN mvn -f /build/interceptor/pom.xml clean package

FROM camunda/zeebe:8.5.0
COPY --from=builder /build/interceptor/target/rate-limit-interceptor.jar \
     /usr/local/zeebe/interceptors/rate-limit-interceptor.jar
COPY configuration/application.yaml /usr/local/zeebe/config/application.yaml