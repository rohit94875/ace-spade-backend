package com.acespade.domain;

import com.acespade.model.enums.GameMode;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "season_player_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "user_id", "game_mode"}))
public class SeasonPlayerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_id", nullable = false)
    private int seasonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 20)
    private GameMode gameMode = GameMode.CLASSIC;

    @Column(name = "matches_played", nullable = false)
    private int matchesPlayed = 0;

    @Column(nullable = false)
    private int wins = 0;

    @Column(nullable = false)
    private int losses = 0;

    @Column(name = "win_streak", nullable = false)
    private int winStreak = 0;

    @Column(name = "loss_streak", nullable = false)
    private int lossStreak = 0;

    @Column(name = "max_win_streak", nullable = false)
    private int maxWinStreak = 0;

    @Column(name = "max_loss_streak", nullable = false)
    private int maxLossStreak = 0;

    @Column(name = "exact_bids", nullable = false)
    private int exactBids = 0;

    @Column(nullable = false)
    private int finishes = 0;

    /** Last ranked classic MMR in the season (used for tier cards at season end). */
    @Column(name = "peak_mmr", nullable = false)
    private double finalMmr = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getSeasonId() { return seasonId; }
    public void setSeasonId(int seasonId) { this.seasonId = seasonId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public GameMode getGameMode() { return gameMode; }
    public void setGameMode(GameMode gameMode) { this.gameMode = gameMode; }
    public int getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getWinStreak() { return winStreak; }
    public void setWinStreak(int winStreak) { this.winStreak = winStreak; }
    public int getLossStreak() { return lossStreak; }
    public void setLossStreak(int lossStreak) { this.lossStreak = lossStreak; }
    public int getMaxWinStreak() { return maxWinStreak; }
    public void setMaxWinStreak(int maxWinStreak) { this.maxWinStreak = maxWinStreak; }
    public int getMaxLossStreak() { return maxLossStreak; }
    public void setMaxLossStreak(int maxLossStreak) { this.maxLossStreak = maxLossStreak; }
    public int getExactBids() { return exactBids; }
    public void setExactBids(int exactBids) { this.exactBids = exactBids; }
    public int getFinishes() { return finishes; }
    public void setFinishes(int finishes) { this.finishes = finishes; }
    public double getFinalMmr() { return finalMmr; }
    public void setFinalMmr(double finalMmr) { this.finalMmr = finalMmr; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
