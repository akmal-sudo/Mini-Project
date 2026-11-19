package com.project.web.service;

import com.project.dbms.entity.RefreshToken;
import com.project.dbms.entity.User;
import com.project.dbms.repository.RefreshTokenRepository;
import com.project.dbms.repository.UserRepository;
import com.project.web.dto.AuthDtos.ChangePasswordRequest;
import com.project.web.dto.LoginRequest;
import com.project.web.dto.LoginResponse;
import com.project.web.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Service managing user authentication, brute-force lockout,
 * password rotation policy, and refresh token lifecycle.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final long accessExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.security.jwt.access-expiration-ms:900000}") long accessExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.accessExpirationMs = accessExpirationMs;
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        // 1. Check account lockout status
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(Instant.now())) {
                throw new LockedException("Account is locked until " + user.getLockedUntil() + " due to repeated failed logins");
            } else {
                // Lockout expired
                user.setLockedUntil(null);
                user.setFailedAttempts(0);
            }
        }

        // 2. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                user.setFailedAttempts(0);
                userRepository.save(user);
                log.warn("User {} locked out for 15 minutes after 5 failed attempts", user.getUsername());
                throw new LockedException("Account locked for 15 minutes after 5 consecutive failed login attempts");
            }

            userRepository.save(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        // 3. Successful login: reset attempts and record last login
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(Instant.now());
        userRepository.save(user);

        // 4. Issue access and refresh tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUsername(), user.getRole(), user.getUserId(), user.getVendorId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // Save refresh token hash
        String tokenHash = hashToken(refreshToken);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getUserId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .revoked(false)
                .build());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresInMs(accessExpirationMs)
                .username(user.getUsername())
                .role(user.getRole())
                .userId(user.getUserId())
                .vendorId(user.getVendorId())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String hash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token is revoked or expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Refresh token rotation: revoke old token
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        // Issue new pair
        String newAccess = jwtTokenProvider.generateAccessToken(
                user.getUsername(), user.getRole(), user.getUserId(), user.getVendorId());
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getUsername());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getUserId())
                .tokenHash(hashToken(newRefresh))
                .expiresAt(Instant.now().plus(Duration.ofDays(7)))
                .revoked(false)
                .build());

        return LoginResponse.builder()
                .accessToken(newAccess)
                .refreshToken(newRefresh)
                .expiresInMs(accessExpirationMs)
                .username(user.getUsername())
                .role(user.getRole())
                .userId(user.getUserId())
                .vendorId(user.getVendorId())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String hash = hashToken(refreshToken);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepository.save(t);
            });
        }
    }

    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password does not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("User {} changed password successfully", user.getUsername());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }
}
