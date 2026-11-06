package hu.eszkozpont.equipment.reference;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record LocationCreateRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*") String code,
        @NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 100) String building, @NotBlank @Size(max = 30) String room) {
    public LocationCreateRequest {
        code = code == null ? null : code.strip().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.strip(); building = building == null ? null : building.strip(); room = room == null ? null : room.strip();
    }
}
