package com.acespade.config;

import com.acespade.service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeasonBootstrap {

    private final SeasonService seasonService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        seasonService.bootstrapSeasonsIfNeeded();
    }
}
