package com.project.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter intercepting requests and applying per-IP Token Bucket rate limiting.
 * Returns HTTP 429 Too Many Requests in RFC 7807 problem+json format on limit exceeded.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitingFilter(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Whitelist static actuator health and websocket handshakes
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        if (!rateLimiter.tryAcquire(clientIp)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"type\":\"urn:problem:too-many-requests\",\"title\":\"Too Many Requests\"," +
                    "\"status\":429,\"detail\":\"Rate limit exceeded: max 100 requests per minute per IP.\"," +
                    "\"instance\":\"" + path + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
