package com.acespade.config;

import com.acespade.service.SeasonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeasonScheduler {

    private final SeasonService seasonService;

    /** Daily at 00:05 IST */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void dailySeasonCheck() {
        log.debug("operation=dailySeasonCheck feature=season-scheduler status=entry");
        seasonService.runSeasonTransitions();
    }
}
