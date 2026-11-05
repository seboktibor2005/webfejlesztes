package hu.eszkozpont.auth;

import hu.eszkozpont.user.UserResponse;
import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {}
