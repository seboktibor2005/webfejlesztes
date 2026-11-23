package hu.eszkozpont.auth;

import hu.eszkozpont.common.ApiException;
import hu.eszkozpont.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final UserMapper mapper;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final String dummyHash;

    public AuthService(UserRepository users, UserMapper mapper, PasswordEncoder passwords, JwtService tokens) {
        this.users = users; this.mapper = mapper; this.passwords = passwords; this.tokens = tokens;
        this.dummyHash = passwords.encode("dummy-password-never-used");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        var email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Ez az e-mail cím már regisztrálva van.");
        }
        var user = new AppUser();
        user.setDisplayName(request.displayName().strip());
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        return mapper.toResponse(users.saveAndFlush(user));
    }

    public LoginResponse login(LoginRequest request) {
        var user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Hibás e-mail cím vagy jelszó.");
        }
        return tokens.issue(user);
    }

    @Transactional
    public void logout(long userId) { users.invalidateTokens(userId); }

    public UserResponse me(long userId) {
        return users.findById(userId).map(mapper::toResponse)
                .orElseThrow(this::invalidToken);
    }

    @Transactional
    public UserResponse updateProfile(long userId, long tokenVersion, ProfileUpdateRequest request) {
        var user = lockCurrentUser(userId, tokenVersion);
        user.setDisplayName(request.displayName().strip());
        return mapper.toResponse(user);
    }

    @Transactional
    public void changePassword(long userId, long tokenVersion, PasswordChangeRequest request) {
        var user = lockCurrentUser(userId, tokenVersion);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INCORRECT_CURRENT_PASSWORD", "A jelenlegi jelszó helytelen.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "Az új jelszó térjen el a jelenlegitől.");
        }
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    private AppUser lockCurrentUser(long userId, long tokenVersion) {
        // Kijelentkezés vagy fiókmódosítás a JWT ellenőrzése után is történhet.
        var user = users.lockById(userId).orElseThrow(this::invalidToken);
        if (!user.isEnabled() || user.getTokenVersion() != tokenVersion) {
            throw invalidToken();
        }
        return user;
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Jelentkezz be újra.");
    }

    private String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
}
