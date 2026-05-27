package com.example;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RateLimitInterceptor implements ServerInterceptor {

    private static final int MAX_PER_WINDOW = 1;
    private static final long WINDOW_MS = 60_000L;

    private static final ConcurrentHashMap<String, long[]> CALLS =
        new ConcurrentHashMap<>();
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
        "authorization",
        Metadata.ASCII_STRING_MARSHALLER
    );
    private static final Pattern SUB_PATTERN = Pattern.compile(
        "\"sub\"\\s*:\\s*\"([^\"]+)\""
    );

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next
    ) {
        String methodName = call.getMethodDescriptor().getFullMethodName();
        if (
            methodName == null || !methodName.contains("CreateProcessInstance")
        ) {
            return next.startCall(call, headers);
        }

        String userId = extractUserId(headers);
        if (userId == null || userId.isBlank()) {
            call.close(Status.UNAUTHENTICATED, new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        long now = System.currentTimeMillis();
        final boolean[] allowed = new boolean[] { true };
        final long[] retryAfterMs = new long[] { 0L };

        CALLS.compute(userId, (key, state) -> {
            if (state == null) {
                state = new long[] { 0L, 0L }; // lastCallMs, count
            }

            long lastCall = state[0];
            long count = state[1];

            if (count == 0 || now - lastCall >= WINDOW_MS) {
                state[0] = now;
                state[1] = 1L;
                allowed[0] = true;
            } else if (count < MAX_PER_WINDOW) {
                state[0] = now;
                state[1] = count + 1L;
                allowed[0] = true;
            } else {
                allowed[0] = false;
                retryAfterMs[0] = WINDOW_MS - (now - lastCall);
            }

            return state;
        });

        if (!allowed[0]) {
            long seconds = Math.max(1L, (retryAfterMs[0] + 999L) / 1000L);
            call.close(
                Status.RESOURCE_EXHAUSTED.withDescription(
                    "Tente novamente em " + seconds + "s"
                ),
                new Metadata()
            );
            return new ServerCall.Listener<ReqT>() {};
        }

        return next.startCall(call, headers);
    }

    private String extractUserId(Metadata headers) {
        String authHeader = headers.get(AUTHORIZATION);
        if (authHeader == null) {
            return null;
        }

        String token = authHeader.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }

        if (token.isEmpty()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        String payload = parts[1];
        String json;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(payload));
            json = new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        Matcher matcher = SUB_PATTERN.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private String padBase64(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        int padding = 4 - remainder;
        return value + "====".substring(0, padding);
    }
}
