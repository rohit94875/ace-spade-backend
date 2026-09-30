package com.acespade.service;

import com.acespade.domain.GameRecordPlayer;
import com.acespade.domain.PlayerRating;
import com.acespade.domain.SeasonPlayerStats;
import com.acespade.domain.SeasonReward;
import com.acespade.model.GameRecord;
import com.acespade.model.enums.GameMode;
import com.acespade.model.enums.RewardSymbolType;
import com.acespade.rating.RewardSymbolUtil;
import com.acespade.rating.TierUtil;
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
import java.util.stream.Collectors;

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
    public void recordRankedResult(int seasonId, Long userId, String gameMode, boolean won, double mmrAfter) {
        String mode = GameMode.normalizeRankedMode(gameMode);
        GameMode modeEnum = GameMode.parse(mode);
        SeasonPlayerStats stats = statsRepository
                .findBySeasonIdAndUserIdAndGameMode(seasonId, userId, modeEnum)
                .orElseGet(() -> {
                    SeasonPlayerStats s = new SeasonPlayerStats();
                    s.setSeasonId(seasonId);
                    s.setUserId(userId);
                    s.setGameMode(modeEnum);
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
        log.debug("operation=recordRankedResult feature=season-rewards-modes status=exit seasonId={} userId={} gameMode={} won={}",
                seasonId, userId, mode, won);
    }

    /** @deprecated use {@link #recordRankedResult} */
    @Transactional
    public void recordRankedClassicResult(int seasonId, Long userId, boolean won, double mmrAfter) {
        recordRankedResult(seasonId, userId, GameMode.CLASSIC.name(), won, mmrAfter);
    }

    @Transactional
    public void finalizeSeasonRewards(int seasonId) {
        for (GameMode mode : GameMode.rankedModes()) {
            backfillSeasonStatsIfNeeded(seasonId, mode);
            computeAndPersistRewards(seasonId, mode.name());
        }
    }

    @Transactional
    public void backfillSeasonStatsIfNeeded(int seasonId, GameMode mode) {
        if (!statsRepository.findBySeasonIdAndGameMode(seasonId, mode).isEmpty()) {
            return;
        }
        List<GameRecord> games = gameRecordRepository.findBySeasonIdAndRankedTrueOrderByPlayedAtAsc(seasonId);
        List<GameRecord> modeGames = games.stream()
                .filter(g -> GameMode.normalizeRankedMode(g.getGameMode()).equals(mode.name()))
                .collect(Collectors.toList());
        if (modeGames.isEmpty()) {
            log.info("operation=backfillSeasonStats feature=season-rewards-modes seasonId={} gameMode={} status=skip no ranked games",
                    seasonId, mode);
            return;
        }
        for (GameRecord game : modeGames) {
            List<GameRecordPlayer> players = gameRecordPlayerRepository.findByGameRecordId(game.getId());
            for (GameRecordPlayer grp : players) {
                if (grp.getUserId() == null) {
                    continue;
                }
                boolean won = grp.getUsername().equals(game.getWinnerUsername());
                double mmrAfter = grp.getRatingAfter() != null ? grp.getRatingAfter() : 0;
                recordRankedResult(seasonId, grp.getUserId(), mode.name(), won, mmrAfter);
            }
        }
        log.info("operation=backfillSeasonStats feature=season-rewards-modes seasonId={} gameMode={} status=exit games={}",
                seasonId, mode, modeGames.size());
    }

    @Transactional
    public void computeAndPersistRewards(int seasonId, String gameMode) {
        String mode = GameMode.normalizeRankedMode(gameMode);
        GameMode modeEnum = GameMode.parse(mode);
        List<SeasonPlayerStats> stats = statsRepository.findBySeasonIdAndGameMode(seasonId, modeEnum);
        for (SeasonPlayerStats s : stats) {
            if (!eligibleForRewards(s)) {
                continue;
            }
            double finalMmr = finalMmrForTierCard(seasonId, s, mode);
            RewardSymbolType tierCard = tierCardForMmr(finalMmr);
            if (tierCard == null) {
                continue;
            }
            upsertTierCard(seasonId, mode, s.getUserId(), tierCard, finalMmr);
        }
        List<SeasonPlayerStats> eligible = stats.stream()
                .filter(SeasonRewardService::eligibleForRewards)
                .collect(Collectors.toList());
        awardTopIfMissing(seasonId, mode, eligible, RewardSymbolType.MOST_MATCHES,
                Comparator.comparingInt(SeasonPlayerStats::getMatchesPlayed));
        awardTopIfMissing(seasonId, mode, eligible, RewardSymbolType.MOST_WINS,
                Comparator.comparingInt(SeasonPlayerStats::getWins));
        awardTopIfMissing(seasonId, mode, eligible, RewardSymbolType.MOST_LOSSES,
                Comparator.comparingInt(SeasonPlayerStats::getLosses));
        awardTopIfMissing(seasonId, mode, eligible, RewardSymbolType.WIN_STREAK,
                Comparator.comparingInt(SeasonPlayerStats::getMaxWinStreak));
        awardTopIfMissing(seasonId, mode, eligible, RewardSymbolType.LOSS_STREAK,
                Comparator.comparingInt(SeasonPlayerStats::getMaxLossStreak));
        awardTopIfMissing(seasonId, mode, eligible, RewardSymbolType.FINISHER,
                Comparator.comparingInt(SeasonPlayerStats::getFinishes));

        if (!rewardRepository.findBySeasonIdAndGameModeAndSymbolType(
                seasonId, mode, RewardSymbolType.TOP_MMR).isPresent()) {
            List<PlayerRating> ratings = playerRatingRepository.findBySeasonIdAndGameModeOrderByRatingDesc(
                    seasonId, mode, PageRequest.of(0, 50));
            ratings.stream()
                    .filter(pr -> pr.getGamesPlayed() >= TierUtil.PLACEMENT_GAMES_REQUIRED)
                    .findFirst()
                    .ifPresent(top -> saveReward(seasonId, mode, top.getUserId(),
                            RewardSymbolType.TOP_MMR, top.getRating()));
        }
        log.info("operation=computeAndPersistRewards feature=season-rewards-modes seasonId={} gameMode={} status=exit",
                seasonId, mode);
    }

    private void upsertTierCard(int seasonId, String gameMode, Long userId,
                                RewardSymbolType tierCard, double finalMmr) {
        Optional<SeasonReward> existing = rewardRepository.findBySeasonIdAndGameMode(seasonId, gameMode).stream()
                .filter(r -> r.getUserId().equals(userId) && RewardSymbolUtil.isTierCard(r.getSymbolType()))
                .findFirst();
        if (existing.isPresent()) {
            SeasonReward reward = existing.get();
            if (reward.getSymbolType() != tierCard || reward.getStatValue() == null
                    || Math.abs(reward.getStatValue() - finalMmr) > 0.01) {
                reward.setSymbolType(tierCard);
                reward.setStatValue(finalMmr);
                rewardRepository.save(reward);
            }
            return;
        }
        saveReward(seasonId, gameMode, userId, tierCard, finalMmr);
    }

    private void awardTopIfMissing(int seasonId, String gameMode, List<SeasonPlayerStats> stats,
                                   RewardSymbolType symbol, Comparator<SeasonPlayerStats> comparator) {
        if (rewardRepository.findBySeasonIdAndGameModeAndSymbolType(seasonId, gameMode, symbol).isPresent()) {
            return;
        }
        Optional<SeasonPlayerStats> top = stats.stream()
                .filter(s -> eligibleForRewards(s) && statValueFor(symbol, s) > 0)
                .max(comparator);
        top.ifPresent(s -> {
            double value = statValueFor(symbol, s);
            if (value > 0) {
                saveReward(seasonId, gameMode, s.getUserId(), symbol, value);
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

    private void saveReward(int seasonId, String gameMode, Long userId,
                            RewardSymbolType symbol, double value) {
        SeasonReward reward = new SeasonReward();
        reward.setSeasonId(seasonId);
        reward.setUserId(userId);
        reward.setGameMode(gameMode);
        reward.setSymbolType(symbol);
        reward.setStatValue(value);
        rewardRepository.save(reward);
    }

    private double finalMmrForTierCard(int seasonId, SeasonPlayerStats stats, String gameMode) {
        return playerRatingRepository
                .findByUserIdAndSeasonIdAndGameMode(stats.getUserId(), seasonId, gameMode)
                .map(PlayerRating::getRating)
                .orElse(stats.getFinalMmr());
    }

    static RewardSymbolType tierCardForMmr(double mmr) {
        String tier = TierUtil.tierForMmr(mmr);
        if (tier.startsWith("Please") || tier.startsWith("Sand")) {
            return RewardSymbolType.SAND_CARD;
        }
        if (tier.startsWith("Bronze")) {
            return RewardSymbolType.BRONZE_CARD;
        }
        if (tier.startsWith("Silver")) {
            return RewardSymbolType.SILVER_CARD;
        }
        if (tier.startsWith("Gold")) {
            return RewardSymbolType.GOLD_CARD;
        }
        if (tier.startsWith("Platinum")) {
            return RewardSymbolType.PLATINUM_CARD;
        }
        if (tier.startsWith("Diamond")) {
            return RewardSymbolType.DIAMOND_CARD;
        }
        return RewardSymbolType.ACE_CARD;
    }

    static boolean eligibleForRewards(SeasonPlayerStats stats) {
        return stats != null && stats.getMatchesPlayed() >= TierUtil.PLACEMENT_GAMES_REQUIRED;
    }

    public static int minRankedGamesForRewards() {
        return TierUtil.PLACEMENT_GAMES_REQUIRED;
    }
}
