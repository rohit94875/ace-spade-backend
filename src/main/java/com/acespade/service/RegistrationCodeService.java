package com.acespade.service;

import com.acespade.domain.RegistrationSettings;
import com.acespade.repository.RegistrationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationCodeService {

    private static final int CODE_BYTES = 24;

    private final RegistrationSettingsRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public RegistrationSettings getOrCreate() {
        return repository.findById(RegistrationSettings.SINGLETON_ID)
                .orElseGet(this::createInitial);
    }

    @Transactional
    public void validateCode(String submitted) {
        if (submitted == null || submitted.isBlank()) {
            throw new IllegalArgumentException("Registration code is required");
        }
        RegistrationSettings settings = getOrCreate();
        if (!constantTimeEquals(submitted.trim(), settings.getRegistrationCode())) {
            throw new IllegalArgumentException("Invalid registration code");
        }
    }

    /** Call after a successful registration to issue the next invite code. */
    @Transactional
    public String rotateAfterRegistration() {
        RegistrationSettings settings = getOrCreate();
        String next = generateCode();
        settings.setRegistrationCode(next);
        settings.setUpdatedAt(Instant.now());
        repository.save(settings);
        log.info("operation=rotateRegistrationCode feature=admin-registration status=exit");
        return next;
    }

    @Transactional(readOnly = true)
    public String getCodeForAdmin() {
        return getOrCreate().getRegistrationCode();
    }

    private RegistrationSettings createInitial() {
        RegistrationSettings settings = new RegistrationSettings();
        settings.setId(RegistrationSettings.SINGLETON_ID);
        settings.setRegistrationCode(generateCode());
        settings.setUpdatedAt(Instant.now());
        settings = repository.save(settings);
        log.info("operation=initRegistrationCode feature=admin-registration status=exit"
                + " hint=set is_admin on your user row to view this code in the admin UI");
        return settings;
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
