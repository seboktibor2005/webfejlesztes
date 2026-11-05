package hu.eszkozpont.equipment;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record EquipmentCreateRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String inventoryCode,
        @NotBlank @Size(max = 120) String name, @Size(max = 2000) String description,
        @NotNull @Positive Long categoryId, @NotNull @Positive Long locationId) {
    public EquipmentCreateRequest {
        inventoryCode = inventoryCode == null ? null : inventoryCode.strip().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.strip();
        description = description == null ? null : description.strip();
    }
}
