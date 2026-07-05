package com.acespade.controller;

import com.acespade.dto.*;
import com.acespade.model.GameRecord;
import com.acespade.repository.GameRecordRepository;
import com.acespade.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<CreateRoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.ok(roomService.createRoom(
                request.getUsername(),
                request.isPlayWithBot(),
                request.getDisconnectPolicy()));
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String code,
                                      @Valid @RequestBody JoinRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.joinRoom(code.toUpperCase(), request.getUsername()));
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

    @GetMapping("/scores/{roomCode}")
    public ResponseEntity<List<GameRecord>> getScores(@PathVariable String roomCode) {
        return ResponseEntity.ok(
                gameRecordRepository.findByRoomCodeOrderByPlayedAtDesc(roomCode.toUpperCase()));
    }
}
