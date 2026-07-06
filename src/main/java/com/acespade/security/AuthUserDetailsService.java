package com.acespade.security;

import com.acespade.domain.User;
import com.acespade.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toAuthUser(user);
    }

    public AuthUser loadById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toAuthUser(user);
    }

    private AuthUser toAuthUser(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getUsername(), user.getPasswordHash());
    }
}
