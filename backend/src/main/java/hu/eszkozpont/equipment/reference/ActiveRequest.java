package hu.eszkozpont.equipment.reference;

import jakarta.validation.constraints.*;

public record ActiveRequest(@NotNull Boolean active, @NotNull @PositiveOrZero Long version) {}
