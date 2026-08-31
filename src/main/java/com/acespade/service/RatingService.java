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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final ObjectMapper objectMapper;
    private final SeasonService seasonService;
    private final SeasonRewardService seasonRewardService;

    private static final String CLASSIC_MODE = SeasonService.CLASSIC_MODE;

    @Value("${ace.rating.forfeit-base-mmr:25.0}")
    private double forfeitBaseMmr;

    public double leavePenaltyMmr(int leaveCount) {
        return Math.pow(2, leaveCount) * forfeitBaseMmr;
    }

    public PlayerRating getOrCreateRating(Long userId) {
        int seasonId = seasonService.getRankedSeasonId();
        return playerRatingRepository.findByUserIdAndSeasonIdAndGameMode(userId, seasonId, CLASSIC_MODE)
                .orElseGet(() -> {
                    PlayerRating rating = new PlayerRating();
                    rating.setUserId(userId);
                    rating.setSeasonId(seasonId);
                    rating.setGameMode(CLASSIC_MODE);
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
                .leaveCount(rating.getLeaveCount())
                .nextLeavePenaltyMmr(Math.round(leavePenaltyMmr(rating.getLeaveCount()) * 10.0) / 10.0)
                .build();
    }

    public PublicUserProfileDto toPublicProfile(User user, PlayerRating rating) {
        boolean placementComplete = TierUtil.isPlacementComplete(rating.getPlacementGames());
        return PublicUserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .mmr(Math.round(rating.getRating() * 10.0) / 10.0)
                .tier(TierUtil.tierBadge(rating.getPlacementGames(), rating.getRating()))
                .placementComplete(placementComplete)
                .placementGames(rating.getPlacementGames())
                .placementRequired(TierUtil.PLACEMENT_GAMES_REQUIRED)
                .gamesPlayed(rating.getGamesPlayed())
                .seasonId(rating.getSeasonId())
                .leaveCount(rating.getLeaveCount())
                .nextLeavePenaltyMmr(Math.round(leavePenaltyMmr(rating.getLeaveCount()) * 10.0) / 10.0)
                .build();
    }

    public PublicUserProfileDto getPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        PlayerRating rating = getOrCreateRating(userId);
        return toPublicProfile(user, rating);
    }

    /** Tier badge for in-game display; null if still in placement. */
    public String tierBadgeForUser(Long userId) {
        if (userId == null) {
            return null;
        }
        PlayerRating rating = getOrCreateRating(userId);
        return TierUtil.tierBadge(rating.getPlacementGames(), rating.getRating());
    }

    @Transactional
    public Map<String, RatingDeltaDto> processRankedGame(GameRecord record, List<Player> humanPlayers,
                                                         Map<String, Integer> scores) {
        List<Player> rankedHumans = humanPlayers.stream()
                .filter(p -> p.getUserId() != null)
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
        List<Integer> ranks = competitionRanks(rankedHumans, scores);

        for (int i = 0; i < rankedHumans.size(); i++) {
            Player p = rankedHumans.get(i);
            PlayerRating pr = getOrCreateRating(p.getUserId());
            before.add(new GlickoRating(pr.getRating(), pr.getRatingDeviation(), pr.getVolatility()));
            ratingEntities.add(pr);
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
            pr.setPlacementGames(pr.getPlacementGames() + 1);
            pr.setUpdatedAt(Instant.now());
            playerRatingRepository.save(pr);

            RatingHistory history = new RatingHistory();
            history.setUserId(p.getUserId());
            history.setSeasonId(pr.getSeasonId());
            history.setGameMode(CLASSIC_MODE);
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

            final int playerRank = ranks.get(i);
            seasonService.getActiveOrGraceSeason().ifPresent(season -> {
                if (season.isRewardsTracked()) {
                    boolean won = playerRank == 1;
                    seasonRewardService.recordRankedClassicResult(season.getId(), p.getUserId(), won, afterRating);
                }
            });
        }

        return deltas;
    }

    /** Same game score → same rank (draw); Glicko treats equal ranks as 0.5 vs each other. */
    static List<Integer> competitionRanks(List<Player> sortedByScoreDesc, Map<String, Integer> scores) {
        List<Integer> ranks = new ArrayList<>(sortedByScoreDesc.size());
        int rank = 1;
        for (int i = 0; i < sortedByScoreDesc.size(); i++) {
            if (i > 0) {
                int prev = scores.getOrDefault(sortedByScoreDesc.get(i - 1).getId(), 0);
                int curr = scores.getOrDefault(sortedByScoreDesc.get(i).getId(), 0);
                if (curr < prev) {
                    rank = i + 1;
                }
            }
            ranks.add(rank);
        }
        return ranks;
    }

    /**
     * Ranked forfeit: only the leaver loses MMR (2^n × base); remaining players unchanged.
     */
    @Transactional
    public Map<String, RatingDeltaDto> processRankedForfeit(GameRecord record, Player forfeiter,
                                                             List<Player> allPlayers) {
        if (forfeiter.getUserId() == null) {
            log.warn("Forfeit in ranked game {} had no authenticated forfeiter — skipping rating", record.getId());
            return Collections.emptyMap();
        }

        PlayerRating pr = getOrCreateRating(forfeiter.getUserId());
        double beforeRating = pr.getRating();
        int n = pr.getLeaveCount();
        double penalty = leavePenaltyMmr(n);
        double afterRating = Math.max(0, beforeRating - penalty);
        double delta = afterRating - beforeRating;

        pr.setRating(afterRating);
        pr.setLeaveCount(n + 1);
        pr.setGamesPlayed(pr.getGamesPlayed() + 1);
        pr.setPlacementGames(pr.getPlacementGames() + 1);
        pr.setUpdatedAt(Instant.now());
        playerRatingRepository.save(pr);

        log.info("Ranked forfeit penalty userId={} leaveCount={} penalty={} rating {} -> {}",
                forfeiter.getUserId(), n, penalty, beforeRating, afterRating);

        RatingHistory history = new RatingHistory();
        history.setUserId(forfeiter.getUserId());
        history.setSeasonId(pr.getSeasonId());
        history.setGameMode(CLASSIC_MODE);
        history.setGameRecordId(record.getId());
        history.setRatingBefore(beforeRating);
        history.setRatingAfter(afterRating);
        history.setRatingDelta(delta);
        ratingHistoryRepository.save(history);

        GameRecordPlayer grp = new GameRecordPlayer();
        grp.setGameRecordId(record.getId());
        grp.setUserId(forfeiter.getUserId());
        grp.setUsername(forfeiter.getUsername());
        grp.setScore(0);
        grp.setRatingBefore(beforeRating);
        grp.setRatingAfter(afterRating);
        grp.setRatingDelta(delta);
        gameRecordPlayerRepository.save(grp);

        Map<String, RatingDeltaDto> deltas = new LinkedHashMap<>();
        deltas.put(forfeiter.getId(), RatingDeltaDto.builder()
                .userId(forfeiter.getUserId())
                .username(forfeiter.getUsername())
                .ratingBefore(Math.round(beforeRating * 10.0) / 10.0)
                .ratingAfter(Math.round(afterRating * 10.0) / 10.0)
                .ratingDelta(Math.round(delta * 10.0) / 10.0)
                .tier(TierUtil.tierBadge(pr.getPlacementGames(), afterRating))
                .placementComplete(TierUtil.isPlacementComplete(pr.getPlacementGames()))
                .placementGames(pr.getPlacementGames())
                .build());
        return deltas;
    }

    public List<LeaderboardEntryDto> getLeaderboard(int limit) {
        return getLeaderboardForSeason(seasonService.getRankedSeasonId(), CLASSIC_MODE, limit);
    }

    public List<LeaderboardEntryDto> getLeaderboardForSeason(int seasonId, String gameMode, int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        List<PlayerRating> ratings = playerRatingRepository.findBySeasonIdAndGameModeOrderByRatingDesc(
                seasonId, gameMode, PageRequest.of(0, capped));

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
                    List<OpponentScoreDto> opponents = Collections.emptyList();
                    int placement = 1;
                    if (gr != null) {
                        try {
                            Map<String, Integer> scores = objectMapper.readValue(
                                    gr.getPlayerScoresJson(),
                                    new TypeReference<Map<String, Integer>>() {});
                            List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
                                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                                    .collect(Collectors.toList());
                            for (int i = 0; i < sorted.size(); i++) {
                                if (sorted.get(i).getKey().equals(grp.getUsername())) {
                                    placement = i + 1;
                                    break;
                                }
                            }
                            opponents = sorted.stream()
                                    .filter(e -> !e.getKey().equals(grp.getUsername()))
                                    .map(e -> OpponentScoreDto.builder()
                                            .username(e.getKey())
                                            .score(e.getValue())
                                            .build())
                                    .collect(Collectors.toList());
                        } catch (Exception e) {
                            log.warn("Failed to parse scores for game {}", gr.getId(), e);
                        }
                    }
                    return MatchHistoryEntryDto.builder()
                            .gameRecordId(grp.getGameRecordId())
                            .roomCode(gr != null ? gr.getRoomCode() : "")
                            .score(grp.getScore())
                            .won(gr != null && grp.getUsername().equals(gr.getWinnerUsername()))
                            .ratingBefore(grp.getRatingBefore())
                            .ratingAfter(grp.getRatingAfter())
                            .ratingDelta(grp.getRatingDelta())
                            .playedAt(playedAt)
                            .ranked(gr != null && gr.isRanked())
                            .maxRounds(gr != null ? gr.getMaxRounds() : 0)
                            .playerCount(gr != null ? gr.getPlayerCount() : 0)
                            .placement(placement)
                            .winnerUsername(gr != null ? gr.getWinnerUsername() : null)
                            .winnerScore(gr != null ? gr.getWinnerScore() : 0)
                            .opponents(opponents)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
