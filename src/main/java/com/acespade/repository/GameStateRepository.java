package com.acespade.repository;

import com.acespade.model.GameState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class GameStateRepository {

    private static final String KEY_PREFIX = "game:";
    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(GameState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(KEY_PREFIX + state.getRoomCode(), json, TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize GameState for room {}", state.getRoomCode(), e);
            throw new RuntimeException("Failed to save game state", e);
        }
    }

    public Optional<GameState> findByRoomCode(String roomCode) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + roomCode);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, GameState.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize GameState for room {}", roomCode, e);
            return Optional.empty();
        }
    }

    /** Returns all currently stored game states. Used to build the public room list. */
    public List<GameState> findAll() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        List<GameState> states = new ArrayList<>();
        if (keys == null || keys.isEmpty()) {
            return states;
        }
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return states;
        }
        for (String json : values) {
            if (json == null) {
                continue;
            }
            try {
                states.add(objectMapper.readValue(json, GameState.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize a GameState during findAll", e);
            }
        }
        return states;
    }

    public void delete(String roomCode) {
        redisTemplate.delete(KEY_PREFIX + roomCode);
    }

    public boolean exists(String roomCode) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + roomCode));
    }
}
