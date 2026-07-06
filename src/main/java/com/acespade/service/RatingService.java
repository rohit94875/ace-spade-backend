package com.acespade.service;

import com.acespade.domain.GameRecordPlayer;
import com.acespade.domain.PlayerRating;
import com.acespade.domain.RatingHistory;
import com.acespade.domain.User;
import com.acespade.dto.*;
import com.acespade.model.GameRecord;
import com.acespade.model.Player;
import com.acespade.rating.Glicko2Calculator;
import com.acespade.rating.GlickoRating;
import com.acespade.rating.TierUtil;
import com.acespade.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final PlayerRatingRepository playerRatingRepository;
    private final RatingHistoryRepository ratingHistoryRepository;
    private final GameRecordPlayerRepository gameRecordPlayerRepository;
    private final UserRepository userRepository;
    private final GameRecordRepository gameRecordRepository;

    public PlayerRating getOrCreateRating(Long userId) {
        return playerRatingRepository.findByUserIdAndSeasonId(userId, TierUtil.CURRENT_SEASON_ID)
                .orElseGet(() -> {
                    PlayerRating rating = new PlayerRating();
                    rating.setUserId(userId);
                    rating.setSeasonId(TierUtil.CURRENT_SEASON_ID);
                    return playerRatingRepository.save(rating);
                });
    }

    public UserProfileDto toProfile(User user) {
        PlayerRating rating = getOrCreateRating(user.getId());
        return toProfile(user, rating);
    }

    public UserProfileDto toProfile(User user, PlayerRating rating) {
        boolean placementComplete = TierUtil.isPlacementComplete(rating.getPlacementGames());
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .mmr(Math.round(rating.getRating() * 10.0) / 10.0)
                .tier(TierUtil.tierBadge(rating.getPlacementGames(), rating.getRating()))
                .placementComplete(placementComplete)
                .placementGames(rating.getPlacementGames())
                .placementRequired(TierUtil.PLACEMENT_GAMES_REQUIRED)
                .gamesPlayed(rating.getGamesPlayed())
                .seasonId(rating.getSeasonId())
                .build();
    }

    @Transactional
    public Map<String, RatingDeltaDto> processRankedGame(GameRecord record, List<Player> humanPlayers,
                                                         Map<String, Integer> scores) {
        List<Player> rankedHumans = humanPlayers.stream()
                .filter(p -> !p.isBot() && p.getUserId() != null)
                .sorted((a, b) -> Integer.compare(
                        scores.getOrDefault(b.getId(), 0),
                        scores.getOrDefault(a.getId(), 0)))
                .collect(Collectors.toList());

        if (rankedHumans.size() < 2) {
            log.warn("Ranked game {} had fewer than 2 authenticated humans — skipping rating", record.getId());
            return Collections.emptyMap();
        }

        List<GlickoRating> before = new ArrayList<>();
        List<PlayerRating> ratingEntities = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();

        for (int i = 0; i < rankedHumans.size(); i++) {
            Player p = rankedHumans.get(i);
            PlayerRating pr = getOrCreateRating(p.getUserId());
            before.add(new GlickoRating(pr.getRating(), pr.getRatingDeviation(), pr.getVolatility()));
            ratingEntities.add(pr);
            ranks.add(i + 1);
        }

        List<GlickoRating> after = Glicko2Calculator.updateRatings(before, ranks);
        Map<String, RatingDeltaDto> deltas = new LinkedHashMap<>();

        for (int i = 0; i < rankedHumans.size(); i++) {
            Player p = rankedHumans.get(i);
            PlayerRating pr = ratingEntities.get(i);
            GlickoRating prev = before.get(i);
            GlickoRating next = after.get(i);

            double beforeRating = prev.getRating();
            double afterRating = next.getRating();
            double delta = afterRating - beforeRating;

            pr.setRating(afterRating);
            pr.setRatingDeviation(next.getRatingDeviation());
            pr.setVolatility(next.getVolatility());
            pr.setGamesPlayed(pr.getGamesPlayed() + 1);
            pr.setPlacementGames(Math.min(TierUtil.PLACEMENT_GAMES_REQUIRED, pr.getPlacementGames() + 1));
            pr.setUpdatedAt(Instant.now());
            playerRatingRepository.save(pr);

            RatingHistory history = new RatingHistory();
            history.setUserId(p.getUserId());
            history.setSeasonId(TierUtil.CURRENT_SEASON_ID);
            history.setGameRecordId(record.getId());
            history.setRatingBefore(beforeRating);
            history.setRatingAfter(afterRating);
            history.setRatingDelta(delta);
            ratingHistoryRepository.save(history);

            GameRecordPlayer grp = new GameRecordPlayer();
            grp.setGameRecordId(record.getId());
            grp.setUserId(p.getUserId());
            grp.setUsername(p.getUsername());
            grp.setScore(scores.getOrDefault(p.getId(), 0));
            grp.setRatingBefore(beforeRating);
            grp.setRatingAfter(afterRating);
            grp.setRatingDelta(delta);
            gameRecordPlayerRepository.save(grp);

            deltas.put(p.getId(), RatingDeltaDto.builder()
                    .userId(p.getUserId())
                    .username(p.getUsername())
                    .ratingBefore(Math.round(beforeRating * 10.0) / 10.0)
                    .ratingAfter(Math.round(afterRating * 10.0) / 10.0)
                    .ratingDelta(Math.round(delta * 10.0) / 10.0)
                    .tier(TierUtil.tierBadge(pr.getPlacementGames(), afterRating))
                    .placementComplete(TierUtil.isPlacementComplete(pr.getPlacementGames()))
                    .placementGames(pr.getPlacementGames())
                    .build());
        }

        return deltas;
    }

    public List<LeaderboardEntryDto> getLeaderboard(int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        List<PlayerRating> ratings = playerRatingRepository.findBySeasonIdOrderByRatingDesc(
                TierUtil.CURRENT_SEASON_ID, PageRequest.of(0, capped));

        List<LeaderboardEntryDto> entries = new ArrayList<>();
        int rank = 1;
        for (PlayerRating pr : ratings) {
            if (!TierUtil.isPlacementComplete(pr.getPlacementGames())) {
                continue;
            }
            User user = userRepository.findById(pr.getUserId()).orElse(null);
            if (user == null) continue;
            entries.add(LeaderboardEntryDto.builder()
                    .rank(rank++)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .mmr(Math.round(pr.getRating() * 10.0) / 10.0)
                    .tier(TierUtil.tierForMmr(pr.getRating()))
                    .gamesPlayed(pr.getGamesPlayed())
                    .build());
        }
        return entries;
    }

    public List<MatchHistoryEntryDto> getMatchHistory(Long userId) {
        return gameRecordPlayerRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(grp -> {
                    GameRecord gr = gameRecordRepository.findById(grp.getGameRecordId()).orElse(null);
                    Instant playedAt = gr != null
                            ? gr.getPlayedAt().toInstant(ZoneOffset.UTC)
                            : Instant.now();
                    return MatchHistoryEntryDto.builder()
                            .gameRecordId(grp.getGameRecordId())
                            .roomCode(gr != null ? gr.getRoomCode() : "")
                            .score(grp.getScore())
                            .won(gr != null && grp.getUsername().equals(gr.getWinnerUsername()))
                            .ratingBefore(grp.getRatingBefore())
                            .ratingAfter(grp.getRatingAfter())
                            .ratingDelta(grp.getRatingDelta())
                            .playedAt(playedAt)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
