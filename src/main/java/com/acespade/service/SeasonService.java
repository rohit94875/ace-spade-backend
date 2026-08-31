package com.acespade.service;

import com.acespade.domain.Season;
import com.acespade.domain.SeasonReward;
import com.acespade.dto.*;
import com.acespade.model.enums.GameMode;
import com.acespade.model.enums.RewardSymbolType;
import com.acespade.model.enums.SeasonStatus;
import com.acespade.rating.RewardSymbolUtil;
import com.acespade.repository.SeasonRepository;
import com.acespade.repository.SeasonRewardRepository;
import com.acespade.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final int COUNTDOWN_DAYS = 4;
    public static final String CLASSIC_MODE = GameMode.CLASSIC.name();

    private final SeasonRepository seasonRepository;
    private final SeasonRewardRepository seasonRewardRepository;
    private final SeasonRewardService seasonRewardService;
    private final UserRepository userRepository;

    private static final YearMonth FIRST_TRACKED_MONTH = YearMonth.of(2026, 9);

    @Transactional
    public void bootstrapSeasonsIfNeeded() {
        if (seasonRepository.count() == 0) {
            Season legacy = new Season();
            legacy.setName("Season 1 — Legacy");
            legacy.setRewardsTracked(true);
            ZonedDateTime legacyStart = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, IST);
            legacy.setStartsAt(legacyStart.toInstant());
            // Ends tonight at midnight IST (Aug 31 → Sep 1)
            legacy.setEndsAt(ZonedDateTime.of(2026, 8, 31, 23, 59, 59, 999_000_000, IST).toInstant());
            legacy.setGraceEndsAt(ZonedDateTime.of(2026, 9, 1, 23, 59, 59, 999_000_000, IST).toInstant());
            legacy.setStatus(resolveStatusFromTimeline(legacy.getStartsAt(), legacy.getEndsAt(), legacy.getGraceEndsAt()));
            seasonRepository.save(legacy);
            log.info("operation=bootstrapSeasons feature=season-service status=exit seeded legacy season 1");
        }
        ensureCalendarSeasons();
        repairSeasonStatuses();
        runSeasonTransitions();
    }

    @Transactional
    public void runSeasonTransitions() {
        Instant now = Instant.now();
        List<Season> seasons = seasonRepository.findAll();
        for (Season season : seasons) {
            if (season.getStatus() == SeasonStatus.SCHEDULED && !now.isBefore(season.getStartsAt())) {
                season.setStatus(SeasonStatus.ACTIVE);
                seasonRepository.save(season);
                log.info("operation=runSeasonTransitions feature=season-service seasonId={} status=ACTIVE", season.getId());
            }
        }
        for (Season season : seasons) {
            if (season.getStatus() == SeasonStatus.ACTIVE && now.isAfter(season.getEndsAt())) {
                season.setStatus(SeasonStatus.GRACE);
                seasonRepository.save(season);
                log.info("operation=runSeasonTransitions feature=season-service seasonId={} status=GRACE", season.getId());
            }
        }
        seasons = seasonRepository.findAll();
        for (Season season : seasons) {
            if (season.getStatus() == SeasonStatus.GRACE && now.isAfter(season.getGraceEndsAt())) {
                completeSeason(season);
            }
        }
        ensureCalendarSeasons();
    }

    private void completeSeason(Season season) {
        if (season.isRewardsTracked()) {
            seasonRewardService.finalizeSeasonRewards(season.getId());
        }
        season.setStatus(SeasonStatus.COMPLETED);
        seasonRepository.save(season);
        log.info("operation=completeSeason feature=season-service seasonId={} status=COMPLETED", season.getId());
    }

    /** Backfill stats (if needed) and compute awards for a completed or grace-ending season. */
    @Transactional
    public void finalizeSeasonRewards(int seasonId) {
        seasonRewardService.finalizeSeasonRewards(seasonId);
    }

    private void ensureCalendarSeasons() {
        YearMonth current = YearMonth.now(IST);
        ensureMonthSeason(current);
        ensureMonthSeason(current.plusMonths(1));
    }

    private void ensureMonthSeason(YearMonth ym) {
        if (ym.isBefore(FIRST_TRACKED_MONTH)) {
            return;
        }
        ZonedDateTime start = ym.atDay(1).atStartOfDay(IST);
        Instant startsAt = start.toInstant();
        Optional<Season> existing = seasonRepository.findFirstByStartsAt(startsAt);
        if (existing.isPresent()) {
            return;
        }
        int nextId = seasonRepository.findAll().stream()
                .mapToInt(Season::getId)
                .max()
                .orElse(0) + 1;
        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Season season = new Season();
        season.setName("Season " + nextId + " — " + monthName + " " + ym.getYear());
        season.setStartsAt(startsAt);
        season.setEndsAt(ym.atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(IST).toInstant());
        season.setGraceEndsAt(ym.plusMonths(1).atDay(1).atTime(23, 59, 59, 999_000_000).atZone(IST).toInstant());
        season.setRewardsTracked(true);
        Instant now = Instant.now();
        if (now.isBefore(startsAt)) {
            season.setStatus(SeasonStatus.SCHEDULED);
        } else if (now.isAfter(season.getGraceEndsAt())) {
            season.setStatus(SeasonStatus.COMPLETED);
        } else if (now.isAfter(season.getEndsAt())) {
            season.setStatus(SeasonStatus.GRACE);
        } else {
            season.setStatus(SeasonStatus.ACTIVE);
        }
        seasonRepository.save(season);
        log.info("operation=ensureMonthSeason feature=season-service seasonId={} month={}", season.getId(), ym);
    }

    public Optional<Season> getActiveOrGraceSeason() {
        return seasonRepository.findFirstByStatusInOrderByIdDesc(
                Arrays.asList(SeasonStatus.ACTIVE, SeasonStatus.GRACE));
    }

    public int getRankedSeasonId() {
        return getActiveOrGraceSeason()
                .map(Season::getId)
                .orElse(1);
    }

    public CurrentSeasonDto getCurrentSeasonDto() {
        Season season = getActiveOrGraceSeason()
                .orElseGet(() -> seasonRepository.findAll().stream()
                        .filter(s -> s.getStatus() == SeasonStatus.SCHEDULED)
                        .min(Comparator.comparing(Season::getStartsAt))
                        .orElseGet(() -> seasonRepository.findAllByOrderByIdDesc().stream().findFirst()
                                .orElseThrow(() -> new IllegalStateException("No seasons configured"))));
        return toCurrentDto(season);
    }

    public List<SeasonSummaryDto> listSeasons() {
        return seasonRepository.findAllByOrderByIdDesc().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    public SeasonDetailDto getSeasonDetail(int seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found"));
        List<SeasonRewardWinnerDto> winners = seasonRewardRepository.findBySeasonId(seasonId).stream()
                .filter(r -> RewardSymbolUtil.isAwardBadge(r.getSymbolType()))
                .map(r -> SeasonRewardWinnerDto.builder()
                        .symbolType(r.getSymbolType())
                        .userId(r.getUserId())
                        .username(userRepository.findById(r.getUserId())
                                .map(u -> u.getUsername())
                                .orElse("Unknown"))
                        .statValue(r.getStatValue())
                        .build())
                .sorted(Comparator.comparing(w -> RewardSymbolUtil.awardPrestigeRank(w.getSymbolType())))
                .collect(Collectors.toList());
        return SeasonDetailDto.builder()
                .seasonId(season.getId())
                .name(season.getName())
                .status(season.getStatus())
                .startsAt(toIstOffset(season.getStartsAt()))
                .endsAt(toIstOffset(season.getEndsAt()))
                .graceEndsAt(toIstOffset(season.getGraceEndsAt()))
                .rewardsTracked(season.isRewardsTracked())
                .awardWinners(winners)
                .build();
    }

    public List<SeasonRewardDto> getMyRewards(int seasonId, Long userId) {
        return sortRewardDtos(seasonRewardRepository.findBySeasonIdAndUserId(seasonId, userId));
    }

    public List<SeasonRewardsGroupDto> getAllMyRewards(Long userId) {
        return getUserRewards(userId);
    }

    public List<SeasonRewardsGroupDto> getUserRewards(Long userId) {
        Map<Integer, List<SeasonReward>> bySeason = seasonRewardRepository.findByUserIdOrderBySeasonIdDesc(userId)
                .stream()
                .collect(Collectors.groupingBy(SeasonReward::getSeasonId));

        return seasonRepository.findAllByOrderByIdDesc().stream()
                .filter(s -> bySeason.containsKey(s.getId()))
                .map(s -> SeasonRewardsGroupDto.builder()
                        .seasonId(s.getId())
                        .seasonName(s.getName())
                        .status(s.getStatus())
                        .rewardsTracked(s.isRewardsTracked())
                        .rewards(sortRewardDtos(bySeason.getOrDefault(s.getId(), Collections.emptyList())))
                        .build())
                .collect(Collectors.toList());
    }

    private List<SeasonRewardDto> sortRewardDtos(List<SeasonReward> rewards) {
        return rewards.stream()
                .map(r -> SeasonRewardDto.builder()
                        .symbolType(r.getSymbolType())
                        .statValue(r.getStatValue())
                        .build())
                .sorted(Comparator
                        .comparingInt((SeasonRewardDto r) -> rewardSortGroup(r.getSymbolType()))
                        .thenComparingInt(r -> rewardSortRank(r.getSymbolType())))
                .collect(Collectors.toList());
    }

    private static int rewardSortGroup(RewardSymbolType type) {
        if (RewardSymbolUtil.isAwardBadge(type)) {
            return 0;
        }
        if (RewardSymbolUtil.isTierCard(type)) {
            return 1;
        }
        return 2;
    }

    private static int rewardSortRank(RewardSymbolType type) {
        if (RewardSymbolUtil.isAwardBadge(type)) {
            return RewardSymbolUtil.awardPrestigeRank(type);
        }
        if (RewardSymbolUtil.isTierCard(type)) {
            return tierCardPrestigeRank(type);
        }
        return 99;
    }

    private static int tierCardPrestigeRank(RewardSymbolType type) {
        switch (type) {
            case ACE_CARD: return 0;
            case DIAMOND_CARD: return 1;
            case PLATINUM_CARD: return 2;
            case GOLD_CARD: return 3;
            case SILVER_CARD: return 4;
            case BRONZE_CARD: return 5;
            case SAND_CARD: return 6;
            default: return 99;
        }
    }

    private CurrentSeasonDto toCurrentDto(Season season) {
        Instant now = Instant.now();
        boolean inGrace = season.getStatus() == SeasonStatus.GRACE;
        boolean scheduled = season.getStatus() == SeasonStatus.SCHEDULED;
        Instant countdownTarget;
        if (inGrace) {
            countdownTarget = season.getGraceEndsAt();
        } else if (scheduled) {
            countdownTarget = season.getStartsAt();
        } else {
            countdownTarget = season.getEndsAt();
        }
        long secondsRemaining = Math.max(0, ChronoUnit.SECONDS.between(now, countdownTarget));
        boolean showCountdown = false;
        String countdownMessage = null;
        if (season.getStatus() == SeasonStatus.ACTIVE) {
            long hoursUntilEnd = ChronoUnit.HOURS.between(now, season.getEndsAt());
            showCountdown = hoursUntilEnd >= 0 && hoursUntilEnd <= COUNTDOWN_DAYS * 24L;
            countdownMessage = showCountdown
                    ? "Season ends soon — finish your ranked games!"
                    : "Ranked classic games count toward this season's rewards.";
        } else if (inGrace) {
            showCountdown = true;
            countdownMessage = "Grace period — final games still count toward this season";
        } else if (scheduled) {
            long hoursUntilStart = ChronoUnit.HOURS.between(now, season.getStartsAt());
            showCountdown = hoursUntilStart >= 0 && hoursUntilStart <= COUNTDOWN_DAYS * 24L;
            countdownMessage = "New season starts soon — get ready!";
        }
        return CurrentSeasonDto.builder()
                .seasonId(season.getId())
                .name(season.getName())
                .status(season.getStatus())
                .startsAt(toIstOffset(season.getStartsAt()))
                .endsAt(toIstOffset(season.getEndsAt()))
                .graceEndsAt(toIstOffset(season.getGraceEndsAt()))
                .rewardsTracked(season.isRewardsTracked())
                .showCountdown(showCountdown)
                .secondsRemaining(secondsRemaining)
                .countdownMessage(countdownMessage)
                .build();
    }

    private SeasonSummaryDto toSummary(Season season) {
        return SeasonSummaryDto.builder()
                .seasonId(season.getId())
                .name(season.getName())
                .status(season.getStatus())
                .startsAt(toIstOffset(season.getStartsAt()))
                .endsAt(toIstOffset(season.getEndsAt()))
                .rewardsTracked(season.isRewardsTracked())
                .build();
    }

    static OffsetDateTime toIstOffset(Instant instant) {
        return instant.atZone(IST).toOffsetDateTime();
    }

    private void repairSeasonStatuses() {
        Instant now = Instant.now();
        for (Season season : seasonRepository.findAll()) {
            SeasonStatus resolved = resolveStatusFromTimeline(
                    season.getStartsAt(), season.getEndsAt(), season.getGraceEndsAt());
            if (season.getStatus() != resolved) {
                season.setStatus(resolved);
                seasonRepository.save(season);
                log.info("operation=repairSeasonStatuses feature=season-service seasonId={} status={}",
                        season.getId(), resolved);
            }
        }
    }

    private static SeasonStatus resolveStatusFromTimeline(Instant startsAt, Instant endsAt, Instant graceEndsAt) {
        Instant now = Instant.now();
        if (now.isBefore(startsAt)) {
            return SeasonStatus.SCHEDULED;
        }
        if (now.isAfter(graceEndsAt)) {
            return SeasonStatus.COMPLETED;
        }
        if (now.isAfter(endsAt)) {
            return SeasonStatus.GRACE;
        }
        return SeasonStatus.ACTIVE;
    }
}
