package com.acespade.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Debounces brief WebSocket drops, then applies a grace period before forfeit / bot-takeover.
 */
@Slf4j
@Component
public class DisconnectScheduler {

    /** Ignore disconnect events shorter than this (STOMP/SockJS flicker, tab refocus). */
    private static final long DISCONNECT_DEBOUNCE_SECONDS = 3;
    private static final long GRACE_SECONDS = 120;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "disconnect-grace");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> graceTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> graceExpiresAt = new ConcurrentHashMap<>();

    public long getGraceSeconds() {
        return GRACE_SECONDS;
    }

    public Long getGraceExpiresAt(String roomCode, String playerId) {
        return graceExpiresAt.get(playerKey(roomCode, playerId));
    }

    /**
     * Waits briefly before marking a player disconnected. Reconnect within the debounce window is ignored.
     */
    public void scheduleDisconnectHandling(String roomCode, String playerId, Runnable onDebouncedDisconnect) {
        cancel(roomCode, playerId);
        String debounceKey = debounceKey(roomCode, playerId);
        ScheduledFuture<?> debounce = executor.schedule(() -> {
            debounceTasks.remove(debounceKey);
            try {
                onDebouncedDisconnect.run();
            } catch (Exception e) {
                log.error("Debounced disconnect failed for {} in {}", playerId, roomCode, e);
            }
        }, DISCONNECT_DEBOUNCE_SECONDS, TimeUnit.SECONDS);
        debounceTasks.put(debounceKey, debounce);
        log.debug("Debouncing disconnect ({}s) for player {} in {}", DISCONNECT_DEBOUNCE_SECONDS, playerId, roomCode);
    }

    public void scheduleDeparture(String roomCode, String playerId, Runnable onExpire) {
        String playerKey = playerKey(roomCode, playerId);
        ScheduledFuture<?> existingGrace = graceTasks.remove(playerKey);
        if (existingGrace != null) {
            existingGrace.cancel(false);
        }

        long expires = System.currentTimeMillis() + GRACE_SECONDS * 1000L;
        graceExpiresAt.put(playerKey, expires);
        ScheduledFuture<?> grace = executor.schedule(() -> {
            graceTasks.remove(playerKey);
            graceExpiresAt.remove(playerKey);
            try {
                onExpire.run();
            } catch (Exception e) {
                log.error("Disconnect grace task failed for {} in {}", playerId, roomCode, e);
            }
        }, GRACE_SECONDS, TimeUnit.SECONDS);
        graceTasks.put(playerKey, grace);
        log.debug("Scheduled disconnect grace ({}s) for player {} in {}", GRACE_SECONDS, playerId, roomCode);
    }

    public void cancel(String roomCode, String playerId) {
        String debounceKey = debounceKey(roomCode, playerId);
        ScheduledFuture<?> debounce = debounceTasks.remove(debounceKey);
        if (debounce != null) {
            debounce.cancel(false);
            log.debug("Cancelled debounced disconnect for player {} in {}", playerId, roomCode);
        }

        String playerKey = playerKey(roomCode, playerId);
        ScheduledFuture<?> grace = graceTasks.remove(playerKey);
        graceExpiresAt.remove(playerKey);
        if (grace != null) {
            grace.cancel(false);
            log.debug("Cancelled disconnect grace for player {} in {}", playerId, roomCode);
        }
    }

    private static String playerKey(String roomCode, String playerId) {
        return roomCode + ":" + playerId;
    }

    private static String debounceKey(String roomCode, String playerId) {
        return roomCode + ":" + playerId + ":debounce";
    }
}
