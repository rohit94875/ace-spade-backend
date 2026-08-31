package com.acespade.controller;

import com.acespade.dto.*;
import com.acespade.security.AuthUser;
import com.acespade.service.RatingService;
import com.acespade.service.SeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seasons")
@RequiredArgsConstructor
public class SeasonController {

    private final SeasonService seasonService;
    private final RatingService ratingService;

    @GetMapping("/current")
    public ResponseEntity<CurrentSeasonDto> current() {
        return ResponseEntity.ok(seasonService.getCurrentSeasonDto());
    }

    @GetMapping
    public ResponseEntity<List<SeasonSummaryDto>> list() {
        return ResponseEntity.ok(seasonService.listSeasons());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable int id) {
        try {
            return ResponseEntity.ok(seasonService.getSeasonDetail(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> seasonLeaderboard(
            @PathVariable int id,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ratingService.getLeaderboardForSeason(
                id, SeasonService.CLASSIC_MODE, limit));
    }

    @GetMapping("/{id}/rewards/me")
    public ResponseEntity<?> myRewards(@PathVariable int id,
                                       @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody("Login required"));
        }
        return ResponseEntity.ok(seasonService.getMyRewards(id, user.getId()));
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
