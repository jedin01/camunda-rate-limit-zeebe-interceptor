package com.example;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RateLimitFilter implements Filter {

    private static final Logger logger = Logger.getLogger(
        RateLimitFilter.class.getName()
    );

    private static final ConcurrentHashMap<String, long[]> userCounters =
        new ConcurrentHashMap<>();

    private static final int MAX_PER_WINDOW = 1;
    private static final long WINDOW_MS = 60_000L;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    public RateLimitFilter() {}

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        boolean isCreateProcess =
            "POST".equalsIgnoreCase(req.getMethod()) &&
            (uri.contains("/v2/process-instances") ||
                uri.contains("/v1/process-instances"));

        if (!isCreateProcess) {
            chain.doFilter(request, response);
            return;
        }

        String userId = extractUserId(req);

        if (userId == null) {
            res.sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Token em falta ou inválido"
            );
            return;
        }

        if (!isAllowed(userId)) {
            long[] state = userCounters.get(userId);
            long remaining =
                state != null
                    ? (WINDOW_MS - (System.currentTimeMillis() - state[1])) /
                      1000
                    : 0;

            logger.info("Rate limit atingido para utilizador: " + userId);

            res.setStatus(HTTP_TOO_MANY_REQUESTS);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write(
                "{\"error\":\"Rate limit atingido\"," +
                    "\"message\":\"Máximo: " +
                    MAX_PER_WINDOW +
                    " processo por minuto.\"," +
                    "\"retryAfter\":" +
                    remaining +
                    "}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}

    private boolean isAllowed(String userId) {
        long now = System.currentTimeMillis();

        userCounters.compute(userId, (key, existing) -> {
            if (existing == null || (now - existing[1]) >= WINDOW_MS) {
                return new long[] { 1, now };
            }
            existing[0]++;
            return existing;
        });

        return userCounters.get(userId)[0] <= MAX_PER_WINDOW;
    }

    private String extractUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");

        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }

        try {
            String[] parts = auth.substring(7).split("\\.");
            if (parts.length < 2) return null;

            String payload = new String(
                Base64.getUrlDecoder().decode(parts[1])
            );

            if (!payload.contains("\"sub\"")) return null;

            String sub = payload.split("\"sub\"\\s*:\\s*\"")[1].split("\"")[0];
            return sub.isEmpty() ? null : sub;
        } catch (Exception e) {
            logger.warning("Erro ao ler JWT: " + e.getMessage());
            return null;
        }
    }
}
