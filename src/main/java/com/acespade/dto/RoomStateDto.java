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
    private int maxRounds;
    private List<PlayerDto> players;
    private Map<String, Integer> scores;
    private String currentTurnPlayerId;
    private String hostPlayerId;
    private boolean playWithBot;
    private boolean ranked;
    private DisconnectPolicy disconnectPolicy;
    private boolean paused;
    private String pausedByPlayerId;
    private List<ChatMessageDto> chatMessages;
    private Map<String, PlayerPresenceDto> presence;
    private List<SpectatorDto> spectators;
    /** targetPlayerId -> list of voter playerIds */
    private Map<String, List<String>> botVotes;
    private String gameMode;
    /** Clan Battle cumulative team scores keyed "1" and "2". */
    private Map<String, Integer> teamScores;
    private String team1Name;
    private String team2Name;
}
