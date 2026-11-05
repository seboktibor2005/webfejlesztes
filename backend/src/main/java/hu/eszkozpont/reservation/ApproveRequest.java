package hu.eszkozpont.reservation;

import jakarta.validation.constraints.*;

public record ApproveRequest(
        @NotNull(message = "A foglalás verziója kötelező.")
        @PositiveOrZero(message = "Érvénytelen verzió.") Long version) {}
