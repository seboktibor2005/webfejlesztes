package hu.eszkozpont.maintenance;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record MaintenanceCreateRequest(@NotNull @Positive Long equipmentId, @NotNull Instant startsAt,
        @NotNull Instant endsAt, @NotBlank @Size(min = 5, max = 500) String reason) {
    public MaintenanceCreateRequest { reason = reason == null ? null : reason.strip(); }
}
