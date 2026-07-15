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
    /** Long-absence window: after this the away player is treated as gone (forfeit / bot takeover). */
    private static final long GRACE_SECONDS = 120;
    /** Per-turn window: how long the table waits on an away player before auto-playing their move. */
    private static final long TURN_TIMEOUT_SECONDS = 45;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "disconnect-grace");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> debounceTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> graceTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> graceExpiresAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> turnTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> turnTimeoutAt = new ConcurrentHashMap<>();

    public long getGraceSeconds() {
        return GRACE_SECONDS;
    }

    public long getTurnTimeoutSeconds() {
        return TURN_TIMEOUT_SECONDS;
    }

    public Long getGraceExpiresAt(String roomCode, String playerId) {
        return graceExpiresAt.get(playerKey(roomCode, playerId));
    }

    public Long getTurnTimeoutAt(String roomCode) {
        return turnTimeoutAt.get(roomCode);
    }

    /**
     * Schedules a single per-room turn timeout. Replaces any existing turn timer for the room.
     * Used to auto-play the current away player's move so the table never stalls.
     */
    public void scheduleTurnTimeout(String roomCode, Runnable onExpire) {
        cancelTurnTimeout(roomCode);
        long expires = System.currentTimeMillis() + TURN_TIMEOUT_SECONDS * 1000L;
        turnTimeoutAt.put(roomCode, expires);
        ScheduledFuture<?> task = executor.schedule(() -> {
            turnTasks.remove(roomCode);
            turnTimeoutAt.remove(roomCode);
            try {
                onExpire.run();
            } catch (Exception e) {
                log.error("Turn timeout task failed for room {}", roomCode, e);
            }
        }, TURN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        turnTasks.put(roomCode, task);
    }

    public void cancelTurnTimeout(String roomCode) {
        ScheduledFuture<?> task = turnTasks.remove(roomCode);
        turnTimeoutAt.remove(roomCode);
        if (task != null) {
            task.cancel(false);
        }
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
