package com.acespade.domain;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "player_ratings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "season_id"}))
public class PlayerRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "season_id", nullable = false)
    private int seasonId = 1;

    @Column(nullable = false)
    private double rating = 1500.0;

    @Column(name = "rating_deviation", nullable = false)
    private double ratingDeviation = 350.0;

    @Column(name = "volatility", nullable = false)
    private double volatility = 0.06;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed = 0;

    @Column(name = "placement_games", nullable = false)
    private int placementGames = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getSeasonId() { return seasonId; }
    public void setSeasonId(int seasonId) { this.seasonId = seasonId; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public double getRatingDeviation() { return ratingDeviation; }
    public void setRatingDeviation(double ratingDeviation) { this.ratingDeviation = ratingDeviation; }
    public double getVolatility() { return volatility; }
    public void setVolatility(double volatility) { this.volatility = volatility; }
    public int getGamesPlayed() { return gamesPlayed; }
    public void setGamesPlayed(int gamesPlayed) { this.gamesPlayed = gamesPlayed; }
    public int getPlacementGames() { return placementGames; }
    public void setPlacementGames(int placementGames) { this.placementGames = placementGames; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
