package hu.eszkozpont.equipment.reference;

import jakarta.validation.constraints.*;

public record CategoryUpdateRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description,
        @NotNull @PositiveOrZero Long version) {
    public CategoryUpdateRequest { name = name == null ? null : name.strip(); description = description == null ? null : description.strip(); }
}
