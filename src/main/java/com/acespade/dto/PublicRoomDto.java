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
}
