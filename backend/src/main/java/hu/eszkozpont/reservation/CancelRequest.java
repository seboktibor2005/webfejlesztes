package hu.eszkozpont.reservation;

import jakarta.validation.constraints.*;

public record CancelRequest(
        @Size(max = 500, message = "Az indok legfeljebb 500 karakter lehet.") String reason,
        @NotNull(message = "A foglalás verziója kötelező.")
        @PositiveOrZero(message = "Érvénytelen verzió.") Long version) {
    public CancelRequest {
        if (reason != null) reason = reason.strip();
    }
}
