package com.acespade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String roomCode;

    @Column(nullable = false)
    private int playerCount;

    /** JSON representation of Map<String, Integer> playerUsername -> finalScore. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String playerScoresJson;

    @Column(nullable = false, length = 100)
    private String winnerUsername;

    @Column(nullable = false)
    private int winnerScore;

    @Column(nullable = false)
    private LocalDateTime playedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean ranked = false;

    @Column(name = "season_id", nullable = false)
    @Builder.Default
    private int seasonId = 1;
}
