package com.acespade.controller;

import com.acespade.dto.AdminUserDto;
import com.acespade.dto.RegistrationCodeDto;
import com.acespade.security.AuthUser;
import com.acespade.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/registration-code")
    public ResponseEntity<?> registrationCode(@AuthenticationPrincipal AuthUser user) {
        try {
            return ResponseEntity.ok(adminService.getRegistrationCode(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(errorBody(e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal AuthUser user) {
        try {
            List<AdminUserDto> users = adminService.listUsers(user);
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(errorBody(e.getMessage()));
        }
    }

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<?> blockUser(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long userId) {
        try {
            return ResponseEntity.ok(adminService.setBlocked(user, userId, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        }
    }

    @PostMapping("/users/{userId}/unblock")
    public ResponseEntity<?> unblockUser(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable Long userId) {
        try {
            return ResponseEntity.ok(adminService.setBlocked(user, userId, false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
        }
    }

    private Map<String, String> errorBody(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
