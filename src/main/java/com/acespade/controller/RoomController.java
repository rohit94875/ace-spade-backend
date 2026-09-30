package com.acespade.controller;

import com.acespade.dto.*;
import com.acespade.model.GameRecord;
import com.acespade.repository.GameRecordRepository;
import com.acespade.security.AuthUser;
import com.acespade.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final GameRecordRepository gameRecordRepository;

    @PostMapping
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest request,
                                        @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Login required to play");
        }
        return ResponseEntity.ok(roomService.createRoom(
                request.getUsername(),
                request.isPlayWithBot(),
                request.getDisconnectPolicy(),
                request.isRanked(),
                request.getMaxRounds(),
                request.isPublicRoom(),
                request.getGameMode(),
                user.getId()));
    }

    @GetMapping("/public")
    public ResponseEntity<List<PublicRoomDto>> listPublicRooms() {
        return ResponseEntity.ok(roomService.listPublicRooms());
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String code,
                                      @Valid @RequestBody JoinRoomRequest request,
                                      @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Login required to play");
        }
        try {
            return ResponseEntity.ok(roomService.joinRoom(code.toUpperCase(), request.getUsername(), user.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{code}/rejoin")
    public ResponseEntity<?> rejoinRoom(@PathVariable String code,
                                        @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Login required to rejoin");
        }
        try {
            return ResponseEntity.ok(roomService.rejoinRoom(code.toUpperCase(), user.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{code}/spectate")
    public ResponseEntity<?> spectateRoom(@PathVariable String code,
                                          @Valid @RequestBody JoinRoomRequest request,
                                          @AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            return ResponseEntity.status(401).body("Login required to spectate");
        }
        try {
            return ResponseEntity.ok(roomService.spectateRoom(
                    code.toUpperCase(), request.getUsername(), user.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{code}/nickname")
    public ResponseEntity<?> updateNickname(@PathVariable String code,
                                            @RequestHeader("X-Session-Token") String sessionToken,
                                            @Valid @RequestBody UpdateNicknameRequest request) {
        try {
            return ResponseEntity.ok(roomService.updateNickname(
                    code.toUpperCase(), sessionToken.trim(), request.getNickname()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> getRoom(@PathVariable String code) {
        try {
            return ResponseEntity.ok(roomService.getRoomState(code.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{code}/settings")
    public ResponseEntity<?> updateSettings(@PathVariable String code,
                                            @RequestHeader("X-Session-Token") String sessionToken,
                                            @Valid @RequestBody RoomSettingsRequest request) {
        try {
            String playerId = roomService.resolvePlayerIdFromSession(sessionToken.trim(), code.toUpperCase());
            return ResponseEntity.ok(roomService.updateRoomSettings(
                    code.toUpperCase(), playerId, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/scores/{roomCode}")
    public ResponseEntity<List<GameRecord>> getScores(@PathVariable String roomCode) {
        return ResponseEntity.ok(
                gameRecordRepository.findByRoomCodeOrderByPlayedAtDesc(roomCode.toUpperCase()));
    }
}
