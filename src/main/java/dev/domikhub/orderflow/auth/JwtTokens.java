package dev.domikhub.orderflow.auth;

import dev.domikhub.orderflow.user.AppUser;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtTokens {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final long seconds;
    public JwtTokens(JwtEncoder encoder, Clock clock, @Value("${app.jwt-issuer}") String issuer,
                     @Value("${app.token-seconds}") long seconds) {
        this.encoder = encoder; this.clock = clock; this.issuer = issuer; this.seconds = seconds;
    }
    public AuthDtos.Token issue(AppUser user) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of("orderflow-api"))
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(seconds))
                .claim("roles", List.of(user.getRole().name())).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new AuthDtos.Token(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), "Bearer", seconds);
    }
}
