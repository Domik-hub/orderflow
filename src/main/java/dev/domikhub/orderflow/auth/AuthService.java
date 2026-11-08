package dev.domikhub.orderflow.auth;

import dev.domikhub.orderflow.common.ApiException;
import dev.domikhub.orderflow.user.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final String dummyHash;
    public AuthService(UserRepository users, PasswordEncoder passwords, JwtTokens tokens) {
        this.users = users; this.passwords = passwords; this.tokens = tokens;
        this.dummyHash = passwords.encode("dummy-password-never-used-for-login");
    }
    @Transactional
    public AuthDtos.UserView register(AuthDtos.Register input) {
        validatePasswordBytes(input.password());
        var email = normalizeEmail(input.email());
        if (users.existsByEmail(email)) throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        var user = new AppUser(email, input.displayName().strip(), passwords.encode(input.password()), AppUser.Role.USER);
        return AuthDtos.UserView.of(users.saveAndFlush(user));
    }
    @Transactional(readOnly = true)
    public AuthDtos.Token login(AuthDtos.Login input) {
        validatePasswordBytes(input.password());
        var user = users.findByEmail(normalizeEmail(input.email()));
        boolean matches = passwords.matches(input.password(), user.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        return tokens.issue(user.get());
    }
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public static void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
    }
}
