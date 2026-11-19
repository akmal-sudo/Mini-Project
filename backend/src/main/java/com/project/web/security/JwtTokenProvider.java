package com.project.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Generates and validates stateless JSON Web Tokens using JJWT 0.12+.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(
            @Value("${app.security.jwt.secret:SuperSecretJwtSigningKeyQuickCommerceSemesterProject2026!}") String secret,
            @Value("${app.security.jwt.access-expiration-ms:900000}") long accessExpirationMs, // 15 mins
            @Value("${app.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) { // 7 days
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(String username, String role, Integer userId, Integer vendorId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpirationMs);

        var builder = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey);

        if (vendorId != null) {
            builder.claim("vendorId", vendorId);
        }

        return builder.compact();
    }

    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshExpirationMs);

        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(username)
                .claim("type", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseAndValidateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
