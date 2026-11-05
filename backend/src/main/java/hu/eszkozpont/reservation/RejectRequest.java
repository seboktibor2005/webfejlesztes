package hu.eszkozpont.reservation;

import jakarta.validation.constraints.*;

public record RejectRequest(
        @NotBlank(message = "Add meg az elutasítás indokát.")
        @Size(min = 5, max = 500, message = "Az indok 5–500 karakter legyen.") String reason,
        @NotNull(message = "A foglalás verziója kötelező.")
        @PositiveOrZero(message = "Érvénytelen verzió.") Long version) {
    public RejectRequest {
        if (reason != null) reason = reason.strip();
    }
}
