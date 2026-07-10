package com.acespade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerPresenceDto {
    private String playerId;
    private String username;
    private boolean connected;
    private boolean bot;
    /** Epoch millis; null when not in disconnect grace. */
    private Long graceExpiresAt;
    /** Epoch millis of last connect/disconnect activity. */
    private long lastSeenAt;
    /** ONLINE | AWAY | GRACE | PAUSED | DISCONNECTED */
    private String status;
    /** Times a turn was auto-played for this player while away. */
    private int autoPlayCount;
    /** Epoch millis when this away player's current turn will be auto-played; null otherwise. */
    private Long turnTimeoutAt;
}
