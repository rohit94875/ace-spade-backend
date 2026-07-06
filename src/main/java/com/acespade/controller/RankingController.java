package com.acespade.controller;

import com.acespade.dto.LeaderboardEntryDto;
import com.acespade.dto.MatchHistoryEntryDto;
import com.acespade.dto.UserProfileDto;
import com.acespade.security.AuthUser;
import com.acespade.service.AuthService;
import com.acespade.service.RatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RatingService ratingService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<?> myRating(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody("Login required"));
        }
        UserProfileDto profile = authService.getProfile(user);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> leaderboard(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ratingService.getLeaderboard(limit));
    }

    @GetMapping("/history/me")
    public ResponseEntity<?> myHistory(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body(errorBody("Login required"));
        }
        List<MatchHistoryEntryDto> history = ratingService.getMatchHistory(user.getId());
        return ResponseEntity.ok(history);
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
