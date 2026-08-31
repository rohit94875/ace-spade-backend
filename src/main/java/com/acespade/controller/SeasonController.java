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

    @org.springframework.beans.factory.annotation.Value("${ace.season.ops-token:}")
    private String seasonOpsToken;

    @GetMapping("/current")
    public ResponseEntity<CurrentSeasonDto> current() {
        return ResponseEntity.ok(seasonService.getCurrentSeasonDto());
    }

    @GetMapping
    public ResponseEntity<List<SeasonSummaryDto>> list() {
        return ResponseEntity.ok(seasonService.listSeasons());
    }

    @GetMapping("/rewards/me")
    public ResponseEntity<?> allMyRewards(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody("Login required"));
        }
        return ResponseEntity.ok(seasonService.getAllMyRewards(user.getId()));
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

    /** Ops: advance season statuses (same as daily cron + boot). Header: X-Season-Ops-Token */
    @PostMapping("/run-transitions")
    public ResponseEntity<?> runTransitions(
            @RequestHeader(value = "X-Season-Ops-Token", required = false) String token) {
        if (!isOpsAuthorized(token)) {
            return ResponseEntity.status(401).body(errorBody("Unauthorized"));
        }
        seasonService.runSeasonTransitions();
        Map<String, String> body = new HashMap<>();
        body.put("status", "ok");
        return ResponseEntity.ok(body);
    }

    /** Ops: backfill stats + compute awards for a season. Header: X-Season-Ops-Token */
    @PostMapping("/{id}/finalize-rewards")
    public ResponseEntity<?> finalizeRewards(
            @PathVariable int id,
            @RequestHeader(value = "X-Season-Ops-Token", required = false) String token) {
        if (!isOpsAuthorized(token)) {
            return ResponseEntity.status(401).body(errorBody("Unauthorized"));
        }
        seasonService.finalizeSeasonRewards(id);
        Map<String, String> body = new HashMap<>();
        body.put("status", "ok");
        body.put("seasonId", String.valueOf(id));
        return ResponseEntity.ok(body);
    }

    private boolean isOpsAuthorized(String token) {
        return seasonOpsToken != null && !seasonOpsToken.isEmpty() && seasonOpsToken.equals(token);
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
