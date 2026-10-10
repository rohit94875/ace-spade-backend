package com.acespade.service;

import com.acespade.domain.User;
import com.acespade.dto.AdminUserDto;
import com.acespade.dto.RegistrationCodeDto;
import com.acespade.repository.RefreshTokenRepository;
import com.acespade.repository.UserRepository;
import com.acespade.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    public static final String BLOCKED_MESSAGE = "The admin has blocked your account";

    private final UserRepository userRepository;
    private final RegistrationCodeService registrationCodeService;
    private final RefreshTokenRepository refreshTokenRepository;

    public void requireAdmin(AuthUser actor) {
        if (actor == null || !actor.isAdmin()) {
            throw new IllegalArgumentException("Admin access required");
        }
    }

    public RegistrationCodeDto getRegistrationCode(AuthUser actor) {
        requireAdmin(actor);
        return RegistrationCodeDto.builder()
                .code(registrationCodeService.getCodeForAdmin())
                .build();
    }

    public List<AdminUserDto> listUsers(AuthUser actor) {
        requireAdmin(actor);
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminUserDto setBlocked(AuthUser actor, Long targetUserId, boolean blocked) {
        requireAdmin(actor);
        if (actor.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot change your own account status");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        target.setBlocked(blocked);
        userRepository.save(target);
        if (blocked) {
            refreshTokenRepository.deleteByUserId(targetUserId);
        }
        return toDto(target);
    }

    private AdminUserDto toDto(User user) {
        return AdminUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .admin(user.isAdmin())
                .blocked(user.isBlocked())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
