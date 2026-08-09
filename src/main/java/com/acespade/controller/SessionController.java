package com.acespade.controller;

import com.acespade.dto.SessionResumeResponse;
import com.acespade.security.AuthUser;
import com.acespade.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final RoomService roomService;

    /**
     * Validates a stored session token and returns full game state for rejoin after refresh.
     */
    @GetMapping("/me")
    public ResponseEntity<SessionResumeResponse> resumeSession(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    SessionResumeResponse.builder().valid(false).message("Missing session token").build());
        }
        SessionResumeResponse response = roomService.resumeSession(token.trim());
        if (!response.isValid()) {
            return ResponseEntity.status(404).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /** Logged-in player with a seat in an active game (for manual rejoin). */
    @GetMapping("/active")
    public ResponseEntity<?> activeGame(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Login required");
        }
        return roomService.findActiveGameForUser(user.getId())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
