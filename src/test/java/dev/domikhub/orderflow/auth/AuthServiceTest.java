package dev.domikhub.orderflow.auth;

import dev.domikhub.orderflow.common.ApiException;
import dev.domikhub.orderflow.user.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test void wrongPasswordNeverProducesAToken() {
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var tokens = mock(JwtTokens.class);
        var user = new AppUser("student@example.com", "Student", "hash", AppUser.Role.USER);
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("wrong-password", "hash")).thenReturn(false);
        var service = new AuthService(users, passwords, tokens);
        assertThatThrownBy(() -> service.login(new AuthDtos.Login("Student@EXAMPLE.com", "wrong-password")))
                .isInstanceOf(ApiException.class).hasMessage("Invalid email or password");
        verifyNoInteractions(tokens);
    }
    @Test void rejectsPasswordsThatExceedBcryptByteLimit() {
        assertThatThrownBy(() -> AuthService.validatePasswordBytes("я".repeat(40))).isInstanceOf(ApiException.class);
    }
}
