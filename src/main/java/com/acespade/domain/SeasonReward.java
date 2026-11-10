package com.acespade.domain;

import com.acespade.model.enums.RewardSymbolType;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "season_rewards",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "user_id", "game_mode", "symbol_type"}))
public class SeasonReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_id", nullable = false)
    private int seasonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_mode", nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'CLASSIC'")
    private String gameMode = "CLASSIC";

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_type", nullable = false, length = 30)
    private RewardSymbolType symbolType;

    @Column(name = "stat_value")
    private Double statValue;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getSeasonId() { return seasonId; }
    public void setSeasonId(int seasonId) { this.seasonId = seasonId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
    public RewardSymbolType getSymbolType() { return symbolType; }
    public void setSymbolType(RewardSymbolType symbolType) { this.symbolType = symbolType; }
    public Double getStatValue() { return statValue; }
    public void setStatValue(Double statValue) { this.statValue = statValue; }
    public Instant getAwardedAt() { return awardedAt; }
    public void setAwardedAt(Instant awardedAt) { this.awardedAt = awardedAt; }
}
