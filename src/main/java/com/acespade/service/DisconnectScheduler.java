package com.acespade.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules delayed disconnect handling so brief WebSocket drops (e.g. page refresh)
 * do not immediately trigger forfeit / bot-takeover.
 */
@Slf4j
@Component
public class DisconnectScheduler {

    private static final long GRACE_SECONDS = 120;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "disconnect-grace");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> graceExpiresAt = new ConcurrentHashMap<>();

    public long getGraceSeconds() {
        return GRACE_SECONDS;
    }

    public Long getGraceExpiresAt(String roomCode, String playerId) {
        return graceExpiresAt.get(key(roomCode, playerId));
    }

    public void scheduleDeparture(String roomCode, String playerId, Runnable onExpire) {
        String key = key(roomCode, playerId);
        cancel(roomCode, playerId);
        long expires = System.currentTimeMillis() + GRACE_SECONDS * 1000L;
        graceExpiresAt.put(key, expires);
        ScheduledFuture<?> future = executor.schedule(() -> {
            pending.remove(key);
            graceExpiresAt.remove(key);
            try {
                onExpire.run();
            } catch (Exception e) {
                log.error("Disconnect grace task failed for {} in {}", playerId, roomCode, e);
            }
        }, GRACE_SECONDS, TimeUnit.SECONDS);
        pending.put(key, future);
        log.debug("Scheduled disconnect grace ({}s) for player {} in {}", GRACE_SECONDS, playerId, roomCode);
    }

    public void cancel(String roomCode, String playerId) {
        String key = key(roomCode, playerId);
        ScheduledFuture<?> future = pending.remove(key);
        graceExpiresAt.remove(key);
        if (future != null) {
            future.cancel(false);
            log.debug("Cancelled disconnect grace for player {} in {}", playerId, roomCode);
        }
    }

    private static String key(String roomCode, String playerId) {
        return roomCode + ":" + playerId;
    }
}
