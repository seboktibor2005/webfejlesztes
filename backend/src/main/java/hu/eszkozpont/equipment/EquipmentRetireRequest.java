package hu.eszkozpont.equipment;

import jakarta.validation.constraints.*;

public record EquipmentRetireRequest(@NotBlank @Size(min = 5, max = 500) String reason,
        @NotNull @PositiveOrZero Long version) {
    public EquipmentRetireRequest { reason = reason == null ? null : reason.strip(); }
}
