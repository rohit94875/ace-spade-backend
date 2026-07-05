package com.acespade.repository;

import com.acespade.model.PlayerSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionRepository {

    private static final String KEY_PREFIX = "session:";
    private static final String PLAYER_KEY_PREFIX = "player:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(PlayerSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(KEY_PREFIX + session.getToken(), json, TTL_HOURS, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(PLAYER_KEY_PREFIX + session.getPlayerId(), session.getToken(),
                    TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PlayerSession for token {}", session.getToken(), e);
            throw new RuntimeException("Failed to save session", e);
        }
    }

    public Optional<PlayerSession> findByToken(String token) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PlayerSession.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PlayerSession for token {}", token, e);
            return Optional.empty();
        }
    }

    public void delete(String token) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (json != null) {
            try {
                PlayerSession session = objectMapper.readValue(json, PlayerSession.class);
                redisTemplate.delete(PLAYER_KEY_PREFIX + session.getPlayerId());
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse session during delete for token {}", token);
            }
        }
        redisTemplate.delete(KEY_PREFIX + token);
    }

    public void deleteByPlayerId(String playerId) {
        String token = redisTemplate.opsForValue().get(PLAYER_KEY_PREFIX + playerId);
        if (token != null) {
            delete(token);
        } else {
            redisTemplate.delete(PLAYER_KEY_PREFIX + playerId);
        }
    }
}
