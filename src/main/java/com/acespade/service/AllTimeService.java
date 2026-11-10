package com.acespade.service;

import com.acespade.dto.AllTimeEntryDto;
import com.acespade.repository.AllTimeStatsRepository;
import com.acespade.repository.AllTimeStatsRepository.AllTimeRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AllTimeService {

    public static final int MIN_GAMES = 25;
    private static final int MAX_LIMIT = 200;

    private final AllTimeStatsRepository repository;

    @Transactional(readOnly = true)
    public List<AllTimeEntryDto> getLeaderboard(String sort, int limit) {
        boolean byWins;
        if ("wins".equalsIgnoreCase(sort)) {
            byWins = true;
        } else if ("winPct".equalsIgnoreCase(sort)) {
            byWins = false;
        } else {
            throw new IllegalArgumentException("sort must be 'wins' or 'winPct'");
        }
        int cap = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<AllTimeEntryDto> entries = new ArrayList<>();
        for (AllTimeRow row : repository.findAllTime(MIN_GAMES)) {
            int games = row.getGames().intValue();
            int wins = row.getWins() == null ? 0 : row.getWins().intValue();
            double pct = games == 0 ? 0 : Math.round(wins * 1000.0 / games) / 10.0;
            entries.add(AllTimeEntryDto.builder()
                    .userId(row.getUserId())
                    .username(row.getUsername())
                    .games(games)
                    .wins(wins)
                    .winPct(pct)
                    .build());
        }

        Comparator<AllTimeEntryDto> byPct = Comparator.comparingDouble(AllTimeEntryDto::getWinPct);
        Comparator<AllTimeEntryDto> byWinCount = Comparator.comparingInt(AllTimeEntryDto::getWins);
        Comparator<AllTimeEntryDto> order = byWins
                ? byWinCount.thenComparing(byPct)
                : byPct.thenComparing(byWinCount);
        entries.sort(order.reversed());

        List<AllTimeEntryDto> top = new ArrayList<>(entries.subList(0, Math.min(cap, entries.size())));
        for (int i = 0; i < top.size(); i++) {
            top.get(i).setRank(i + 1);
        }
        return top;
    }
}
