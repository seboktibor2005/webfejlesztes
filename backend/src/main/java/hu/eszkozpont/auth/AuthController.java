package hu.eszkozpont.auth;

import hu.eszkozpont.user.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/api/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.created(URI.create("/api/me")).body(service.register(request));
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }

    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt) { service.logout(Long.parseLong(jwt.getSubject())); }

    @GetMapping("/api/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) { return service.me(Long.parseLong(jwt.getSubject())); }

    @PutMapping("/api/me")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return service.updateProfile(Long.parseLong(jwt.getSubject()), jwt.<Number>getClaim("tokenVersion").longValue(), request);
    }

    @PostMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(Long.parseLong(jwt.getSubject()), jwt.<Number>getClaim("tokenVersion").longValue(), request);
    }
}
