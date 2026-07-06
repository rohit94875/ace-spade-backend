package com.acespade.service;

import com.acespade.domain.RefreshToken;
import com.acespade.domain.User;
import com.acespade.dto.*;
import com.acespade.repository.RefreshTokenRepository;
import com.acespade.repository.UserRepository;
import com.acespade.security.AuthUser;
import com.acespade.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RatingService ratingService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${ace.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String username = request.getUsername().trim();

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3-20 chars: letters, numbers, underscore");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username already taken");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        ratingService.getOrCreateRating(user.getId());
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        String hash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        refreshTokenRepository.delete(stored);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(AuthUser user) {
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    public UserProfileDto getProfile(AuthUser user) {
        User entity = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ratingService.toProfile(entity);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getUsername());
        String refreshToken = createRefreshToken(user.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresInMs(jwtService.getAccessExpirationMs())
                .user(ratingService.toProfile(user))
                .build();
    }

    private String createRefreshToken(Long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hashToken(raw));
        token.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        refreshTokenRepository.save(token);
        return raw;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
