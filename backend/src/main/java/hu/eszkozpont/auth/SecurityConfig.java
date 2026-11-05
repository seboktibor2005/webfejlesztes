package hu.eszkozpont.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import hu.eszkozpont.common.ApiExceptionHandler;
import hu.eszkozpont.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("A JWT_SECRET legalább 32 bájt hosszú legyen.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, UserRepository users, @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> account = jwt -> {
            try {
                var user = users.findById(Long.parseLong(jwt.getSubject())).orElse(null);
                Number version = jwt.getClaim("tokenVersion");
                if (user != null && user.isEnabled() && version != null && version.longValue() == user.getTokenVersion()
                        && user.getRole().name().equals(jwt.getClaimAsString("role"))
                        && jwt.getAudience() != null && jwt.getAudience().contains(audience)
                        && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(clock.instant())
                        && jwt.getIssuedAt() != null) return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | ClassCastException ignored) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "A belépés lejárt vagy vissza lett vonva.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), account));
        return decoder;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).cors(cors -> {})
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/management/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/**").authenticated().anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            json.writeValue(response.getOutputStream(), ApiExceptionHandler.problem(HttpStatus.UNAUTHORIZED,
                                    "UNAUTHORIZED", "A folytatáshoz jelentkezz be."));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            json.writeValue(response.getOutputStream(), ApiExceptionHandler.problem(HttpStatus.FORBIDDEN,
                                    "ACCESS_DENIED", "Ehhez a művelethez nincs jogosultságod."));
                        }))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            json.writeValue(response.getOutputStream(), ApiExceptionHandler.problem(HttpStatus.UNAUTHORIZED,
                                    "INVALID_TOKEN", "A belépés lejárt vagy érvénytelen. Jelentkezz be újra."));
                        })
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> new JwtAuthenticationToken(token,
                                List.of(new SimpleGrantedAuthority("ROLE_" + token.getClaimAsString("role"))), token.getSubject()))));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origin}") String origin) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origin.split(",")).map(String::strip).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
