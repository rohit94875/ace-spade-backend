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
    /** ONLINE | DISCONNECTED | GRACE | PAUSED */
    private String status;
}
