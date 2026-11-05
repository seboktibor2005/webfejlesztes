package hu.eszkozpont.user;

public record AdminUserResponse(Long id, String displayName, String email, Role role, boolean enabled) {}
