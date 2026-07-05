package com.acespade.model;

import com.acespade.model.enums.DisconnectPolicy;
import com.acespade.model.enums.GamePhase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    private String roomCode;
    private String hostPlayerId;

    @Builder.Default
    private GamePhase phase = GamePhase.LOBBY;

    /** Current round number, 1–13. */
    private int round;

    /**
     * Index into players list of who leads this round's first trick.
     * Advances clockwise (+1 mod numPlayers) each round.
     */
    private int roundLeaderIndex;

    /**
     * Index of the player whose turn it currently is — used for both
     * bidding turns and card-play turns.
     */
    private int currentPlayerIndex;

    /**
     * Index of the player who leads the current trick (winner of last trick).
     * Resets to roundLeaderIndex at the start of each round's first trick.
     */
    private int leadPlayerIndex;

    /** Number of tricks fully resolved in the current round. */
    private int tricksPlayedInRound;

    @Builder.Default
    private List<Player> players = new ArrayList<>();

    @Builder.Default
    private List<TrickCard> currentTrick = new ArrayList<>();

    /** playerId → cumulative score across all completed rounds. */
    @Builder.Default
    private Map<String, Integer> scores = new HashMap<>();

    /** Room option: play vs BOT Vitality from lobby (host + bot). */
    @Builder.Default
    private boolean playWithBot = false;

    /** Room option: what happens when a human leaves mid-game. */
    @Builder.Default
    private DisconnectPolicy disconnectPolicy = DisconnectPolicy.FORFEIT_WIN;

    /** Solo bot games only: game frozen until human resumes. */
    @Builder.Default
    private boolean paused = false;

    /** Player who paused (human); used for resume authorization. */
    private String pausedByPlayerId;

    public Player findPlayer(String playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public int findPlayerIndex(String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(playerId)) return i;
        }
        return -1;
    }
}
