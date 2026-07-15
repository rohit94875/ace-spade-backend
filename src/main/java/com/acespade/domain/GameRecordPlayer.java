package com.acespade.domain;

import javax.persistence.*;

@Entity
@Table(name = "game_record_players")
public class GameRecordPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_record_id", nullable = false)
    private Long gameRecordId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false)
    private int score;

    @Column(name = "rating_before")
    private Double ratingBefore;

    @Column(name = "rating_after")
    private Double ratingAfter;

    @Column(name = "rating_delta")
    private Double ratingDelta;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGameRecordId() { return gameRecordId; }
    public void setGameRecordId(Long gameRecordId) { this.gameRecordId = gameRecordId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public Double getRatingBefore() { return ratingBefore; }
    public void setRatingBefore(Double ratingBefore) { this.ratingBefore = ratingBefore; }
    public Double getRatingAfter() { return ratingAfter; }
    public void setRatingAfter(Double ratingAfter) { this.ratingAfter = ratingAfter; }
    public Double getRatingDelta() { return ratingDelta; }
    public void setRatingDelta(Double ratingDelta) { this.ratingDelta = ratingDelta; }
}
