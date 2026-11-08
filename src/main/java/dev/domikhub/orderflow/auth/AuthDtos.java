package dev.domikhub.orderflow.auth;

import dev.domikhub.orderflow.user.AppUser;
import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Size(min = 2, max = 80) String displayName,
                           @NotBlank @Size(min = 12, max = 64) String password) {}
    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 64) String password) {}
    public record UserView(Long id, String email, String displayName, String role) {
        public static UserView of(AppUser u) { return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name()); }
    }
    public record Token(String accessToken, String tokenType, long expiresIn) {}
}
