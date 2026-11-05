package hu.eszkozpont.equipment;

import jakarta.validation.constraints.*;

public record EquipmentUpdateRequest(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description,
        @NotNull @Positive Long categoryId, @NotNull @Positive Long locationId, @NotNull @PositiveOrZero Long version) {
    public EquipmentUpdateRequest {
        name = name == null ? null : name.strip();
        description = description == null ? null : description.strip();
    }
}
