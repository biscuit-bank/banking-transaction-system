package com.depi.bank.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitingFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) &&
                (path.contains("/auth/") || path.contains("/transactions/") || path.contains("/transfers"))) {
            String key = request.getRemoteAddr() + ":" + path;
            long currentMinute = Instant.now(clock).getEpochSecond() / 60;
            WindowCounter counter = counters.compute(key, (k, existing) -> {
                if (existing == null || existing.minuteWindow != currentMinute) {
                    return new WindowCounter(currentMinute, 1);
                }
                existing.count++;
                return existing;
            });

            if (counter.count > MAX_REQUESTS_PER_MINUTE) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Please wait before retrying.\"}"
                );
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static class WindowCounter {
        final long minuteWindow;
        int count;

        WindowCounter(long minuteWindow, int count) {
            this.minuteWindow = minuteWindow;
            this.count = count;
        }
    }
}