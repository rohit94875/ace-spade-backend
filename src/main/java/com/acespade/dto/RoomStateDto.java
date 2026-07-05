package com.acespade.dto;

import com.acespade.model.enums.DisconnectPolicy;
import com.acespade.model.enums.GamePhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateDto {
    private String roomCode;
    private GamePhase phase;
    private int round;
    private List<PlayerDto> players;
    private Map<String, Integer> scores;
    private String currentTurnPlayerId;
    private String hostPlayerId;
    private boolean playWithBot;
    private DisconnectPolicy disconnectPolicy;
    private boolean paused;
    private String pausedByPlayerId;
}
