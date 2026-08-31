package com.acespade.service;

import com.acespade.domain.PlayerRating;
import com.acespade.domain.SeasonPlayerStats;
import com.acespade.domain.SeasonReward;
import com.acespade.rating.TierUtil;
import com.acespade.model.enums.GameMode;
import com.acespade.model.enums.RewardSymbolType;
import com.acespade.domain.GameRecordPlayer;
import com.acespade.model.GameRecord;
import com.acespade.repository.GameRecordPlayerRepository;
import com.acespade.repository.GameRecordRepository;
import com.acespade.repository.PlayerRatingRepository;
import com.acespade.repository.SeasonPlayerStatsRepository;
import com.acespade.repository.SeasonRewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonRewardService {

    private final SeasonPlayerStatsRepository statsRepository;
    private final SeasonRewardRepository rewardRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final GameRecordRepository gameRecordRepository;
    private final GameRecordPlayerRepository gameRecordPlayerRepository;

    @Transactional
    public void recordRankedClassicResult(int seasonId, Long userId, boolean won, double mmrAfter) {
        SeasonPlayerStats stats = statsRepository
                .findBySeasonIdAndUserIdAndGameMode(seasonId, userId, GameMode.CLASSIC)
                .orElseGet(() -> {
                    SeasonPlayerStats s = new SeasonPlayerStats();
                    s.setSeasonId(seasonId);
                    s.setUserId(userId);
                    s.setGameMode(GameMode.CLASSIC);
                    return s;
                });
        stats.setMatchesPlayed(stats.getMatchesPlayed() + 1);
        if (won) {
            stats.setWins(stats.getWins() + 1);
            stats.setWinStreak(stats.getWinStreak() + 1);
            stats.setLossStreak(0);
            stats.setFinishes(stats.getFinishes() + 1);
            stats.setMaxWinStreak(Math.max(stats.getMaxWinStreak(), stats.getWinStreak()));
        } else {
            stats.setLosses(stats.getLosses() + 1);
            stats.setLossStreak(stats.getLossStreak() + 1);
            stats.setWinStreak(0);
            stats.setMaxLossStreak(Math.max(stats.getMaxLossStreak(), stats.getLossStreak()));
        }
        stats.setFinalMmr(mmrAfter);
        statsRepository.save(stats);
    }

    @Transactional
    public void finalizeSeasonRewards(int seasonId) {
        backfillSeasonStatsIfNeeded(seasonId);
        computeAndPersistRewards(seasonId);
    }

    /** Rebuild season_player_stats from ranked game_records when live tracking was off. */
    @Transactional
    public void backfillSeasonStatsIfNeeded(int seasonId) {
        if (!statsRepository.findBySeasonIdAndGameMode(seasonId, GameMode.CLASSIC).isEmpty()) {
            return;
        }
        List<GameRecord> games = gameRecordRepository.findBySeasonIdAndRankedTrueOrderByPlayedAtAsc(seasonId);
        if (games.isEmpty()) {
            log.info("operation=backfillSeasonStats feature=season-rewards seasonId={} status=skip no ranked games",
                    seasonId);
            return;
        }
        for (GameRecord game : games) {
            List<GameRecordPlayer> players = gameRecordPlayerRepository.findByGameRecordId(game.getId());
            for (GameRecordPlayer grp : players) {
                if (grp.getUserId() == null) {
                    continue;
                }
                boolean won = grp.getUsername().equals(game.getWinnerUsername());
                double mmrAfter = grp.getRatingAfter() != null ? grp.getRatingAfter() : 0;
                recordRankedClassicResult(seasonId, grp.getUserId(), won, mmrAfter);
            }
        }
        log.info("operation=backfillSeasonStats feature=season-rewards seasonId={} status=exit games={}",
                seasonId, games.size());
    }

    @Transactional
    public void computeAndPersistRewards(int seasonId) {
        if (rewardRepository.findBySeasonId(seasonId).stream().findAny().isPresent()) {
            return;
        }
        List<SeasonPlayerStats> stats = statsRepository.findBySeasonIdAndGameMode(seasonId, GameMode.CLASSIC);
        for (SeasonPlayerStats s : stats) {
            if (!eligibleForRewards(s)) {
                continue;
            }
            double finalMmr = finalMmrForTierCard(seasonId, s);
            RewardSymbolType tierCard = tierCardForMmr(finalMmr);
            if (tierCard != null) {
                saveReward(seasonId, s.getUserId(), tierCard, finalMmr);
            }
        }
        List<SeasonPlayerStats> eligible = stats.stream()
                .filter(SeasonRewardService::eligibleForRewards)
                .collect(java.util.stream.Collectors.toList());
        awardTop(seasonId, eligible, RewardSymbolType.MOST_MATCHES,
                Comparator.comparingInt(SeasonPlayerStats::getMatchesPlayed));
        awardTop(seasonId, eligible, RewardSymbolType.MOST_WINS,
                Comparator.comparingInt(SeasonPlayerStats::getWins));
        awardTop(seasonId, eligible, RewardSymbolType.MOST_LOSSES,
                Comparator.comparingInt(SeasonPlayerStats::getLosses));
        awardTop(seasonId, eligible, RewardSymbolType.WIN_STREAK,
                Comparator.comparingInt(SeasonPlayerStats::getMaxWinStreak));
        awardTop(seasonId, eligible, RewardSymbolType.LOSS_STREAK,
                Comparator.comparingInt(SeasonPlayerStats::getMaxLossStreak));
        awardTop(seasonId, eligible, RewardSymbolType.FINISHER,
                Comparator.comparingInt(SeasonPlayerStats::getFinishes));

        List<PlayerRating> ratings = playerRatingRepository.findBySeasonIdAndGameModeOrderByRatingDesc(
                seasonId, GameMode.CLASSIC.name(), PageRequest.of(0, 50));
        ratings.stream()
                .filter(pr -> pr.getGamesPlayed() >= TierUtil.PLACEMENT_GAMES_REQUIRED)
                .findFirst()
                .ifPresent(top -> saveReward(seasonId, top.getUserId(), RewardSymbolType.TOP_MMR, top.getRating()));
        log.info("operation=computeAndPersistRewards feature=season-rewards seasonId={} status=exit", seasonId);
    }

    private void awardTop(int seasonId, List<SeasonPlayerStats> stats, RewardSymbolType symbol,
                          Comparator<SeasonPlayerStats> comparator) {
        Optional<SeasonPlayerStats> top = stats.stream()
                .filter(s -> eligibleForRewards(s) && statValueFor(symbol, s) > 0)
                .max(comparator);
        top.ifPresent(s -> {
            double value = statValueFor(symbol, s);
            if (value > 0) {
                saveReward(seasonId, s.getUserId(), symbol, value);
            }
        });
    }

    private double statValueFor(RewardSymbolType symbol, SeasonPlayerStats s) {
        switch (symbol) {
            case MOST_MATCHES: return s.getMatchesPlayed();
            case MOST_WINS: return s.getWins();
            case MOST_LOSSES: return s.getLosses();
            case WIN_STREAK: return s.getMaxWinStreak();
            case LOSS_STREAK: return s.getMaxLossStreak();
            case FINISHER: return s.getFinishes();
            default: return 0;
        }
    }

    private void saveReward(int seasonId, Long userId, RewardSymbolType symbol, double value) {
        SeasonReward reward = new SeasonReward();
        reward.setSeasonId(seasonId);
        reward.setUserId(userId);
        reward.setSymbolType(symbol);
        reward.setStatValue(value);
        rewardRepository.save(reward);
    }

    private double finalMmrForTierCard(int seasonId, SeasonPlayerStats stats) {
        return playerRatingRepository
                .findByUserIdAndSeasonIdAndGameMode(stats.getUserId(), seasonId, GameMode.CLASSIC.name())
                .map(PlayerRating::getRating)
                .orElse(stats.getFinalMmr());
    }

    static RewardSymbolType tierCardForMmr(double mmr) {
        if (mmr < 1100) return RewardSymbolType.SAND_CARD;
        if (mmr < 1250) return RewardSymbolType.BRONZE_CARD;
        if (mmr < 1400) return RewardSymbolType.SILVER_CARD;
        if (mmr < 1550) return RewardSymbolType.GOLD_CARD;
        if (mmr < 1700) return RewardSymbolType.PLATINUM_CARD;
        if (mmr < 1850) return RewardSymbolType.DIAMOND_CARD;
        return RewardSymbolType.ACE_CARD;
    }

    static boolean eligibleForRewards(SeasonPlayerStats stats) {
        return stats != null && stats.getMatchesPlayed() >= TierUtil.PLACEMENT_GAMES_REQUIRED;
    }

    public static int minRankedGamesForRewards() {
        return TierUtil.PLACEMENT_GAMES_REQUIRED;
    }
}
