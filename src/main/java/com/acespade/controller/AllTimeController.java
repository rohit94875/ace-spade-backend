package com.acespade.controller;

import com.acespade.service.AllTimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/** All-time (cross-season) Classic + Ruthless leaderboard. Read-only. */
@RestController
@RequestMapping("/api/rankings/all-time")
@RequiredArgsConstructor
public class AllTimeController {

    private final AllTimeService allTimeService;

    @GetMapping
    public ResponseEntity<?> allTime(
            @RequestParam(defaultValue = "wins") String sort,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(allTimeService.getLeaderboard(sort, limit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
        }
    }
}
