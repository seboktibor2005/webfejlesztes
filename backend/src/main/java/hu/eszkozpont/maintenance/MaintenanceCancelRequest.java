package hu.eszkozpont.maintenance;

import jakarta.validation.constraints.*;

public record MaintenanceCancelRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
    public MaintenanceCancelRequest { reason = reason == null ? null : reason.strip(); }
}
