package hu.eszkozpont.auth;

import hu.eszkozpont.user.AppUser;
import hu.eszkozpont.user.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final UserMapper mapper;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, UserMapper mapper, Clock clock,
            @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.ttl}") Duration ttl) {
        this.encoder = encoder; this.mapper = mapper; this.clock = clock;
        this.issuer = issuer; this.audience = audience; this.ttl = ttl;
    }

    public LoginResponse issue(AppUser user) {
        var now = clock.instant();
        var expiresAt = now.plus(ttl);
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(user.getId().toString()).issuedAt(now).notBefore(now).expiresAt(expiresAt)
                .claim("role", user.getRole().name()).claim("tokenVersion", user.getTokenVersion()).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new LoginResponse(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                "Bearer", expiresAt, mapper.toResponse(user));
    }
}
