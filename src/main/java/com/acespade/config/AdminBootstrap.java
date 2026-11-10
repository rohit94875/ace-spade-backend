package com.acespade.config;

import com.acespade.domain.User;
import com.acespade.repository.UserRepository;
import com.acespade.service.RegistrationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RegistrationCodeService registrationCodeService;

    @Value("${ace.admin.bootstrap-email:}")
    private String bootstrapAdminEmail;

    @Override
    public void run(ApplicationArguments args) {
        registrationCodeService.getOrCreate();
        if (!StringUtils.hasText(bootstrapAdminEmail)) {
            return;
        }
        String email = bootstrapAdminEmail.trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (!user.isAdmin()) {
                user.setAdmin(true);
                userRepository.save(user);
                log.info("operation=adminBootstrap feature=admin-registration status=exit email={}", email);
            }
        });
    }
}
