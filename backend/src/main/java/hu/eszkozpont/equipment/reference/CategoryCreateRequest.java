package hu.eszkozpont.equipment.reference;

import jakarta.validation.constraints.*;

public record CategoryCreateRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description) {
    public CategoryCreateRequest { name = name == null ? null : name.strip(); description = description == null ? null : description.strip(); }
}
