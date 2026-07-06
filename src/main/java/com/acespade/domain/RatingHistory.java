package com.acespade.domain;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "season_id", nullable = false)
    private int seasonId;

    @Column(name = "game_record_id")
    private Long gameRecordId;

    @Column(name = "rating_before", nullable = false)
    private double ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private double ratingAfter;

    @Column(name = "rating_delta", nullable = false)
    private double ratingDelta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getSeasonId() { return seasonId; }
    public void setSeasonId(int seasonId) { this.seasonId = seasonId; }
    public Long getGameRecordId() { return gameRecordId; }
    public void setGameRecordId(Long gameRecordId) { this.gameRecordId = gameRecordId; }
    public double getRatingBefore() { return ratingBefore; }
    public void setRatingBefore(double ratingBefore) { this.ratingBefore = ratingBefore; }
    public double getRatingAfter() { return ratingAfter; }
    public void setRatingAfter(double ratingAfter) { this.ratingAfter = ratingAfter; }
    public double getRatingDelta() { return ratingDelta; }
    public void setRatingDelta(double ratingDelta) { this.ratingDelta = ratingDelta; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
