package com.acespade.dto;

import lombok.Builder;
import lombok.Data;

/** Summary of an open, joinable public room shown in the lobby browser. */
@Data
@Builder
public class PublicRoomDto {
    private String roomCode;
    private String hostUsername;
    private int playerCount;
    private int maxPlayers;
    private boolean ranked;
    private int maxRounds;
    private boolean playWithBot;
    /** True when the match is in progress and open for spectators. */
    private boolean spectatable;
    private String phase;
    private int spectatorCount;
}
