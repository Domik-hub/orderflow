package dev.domikhub.orderflow.auth;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final CurrentUser current;
    public AuthController(AuthService auth, CurrentUser current) { this.auth = auth; this.current = current; }
    @SecurityRequirements @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserView register(@Valid @RequestBody AuthDtos.Register input) { return auth.register(input); }
    @SecurityRequirements @PostMapping("/login")
    public AuthDtos.Token login(@Valid @RequestBody AuthDtos.Login input) { return auth.login(input); }
    @GetMapping("/me")
    public AuthDtos.UserView me(@AuthenticationPrincipal Jwt jwt) { return AuthDtos.UserView.of(current.require(jwt)); }
}
