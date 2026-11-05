package hu.eszkozpont.user;

import jakarta.validation.constraints.*;

public record AdminUserUpdateRequest(@NotBlank @Size(max = 100) String displayName,
        @NotNull Role role, @NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) {
    public AdminUserUpdateRequest { displayName = displayName == null ? null : displayName.strip(); }
}
