package dev.domikhub.orderflow.config;

import dev.domikhub.orderflow.auth.AuthService;
import dev.domikhub.orderflow.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    public AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${app.admin-email}") String email, @Value("${app.admin-password}") String password) {
        this.users = users; this.passwords = passwords; this.email = email; this.password = password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (!email.contains("@") || password.length() < 12 || password.length() > 64)
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD (12-64 characters) must both be configured");
        AuthService.validatePasswordBytes(password);
        var normalized = AuthService.normalizeEmail(email);
        var existing = users.findByEmail(normalized);
        if (existing.isPresent()) {
            if (existing.get().getRole() != AppUser.Role.ADMIN)
                throw new IllegalStateException("Bootstrap admin email belongs to a regular user; refusing to grant privileges");
            return;
        }
        users.save(new AppUser(normalized, "Administrator", passwords.encode(password), AppUser.Role.ADMIN));
    }
}
