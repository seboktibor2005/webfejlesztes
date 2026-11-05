package hu.eszkozpont.user;

public record UserResponse(Long id, String displayName, String email, Role role) {}
