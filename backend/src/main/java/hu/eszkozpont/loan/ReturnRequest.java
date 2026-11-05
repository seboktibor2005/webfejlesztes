package hu.eszkozpont.loan;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record ReturnRequest(@NotNull ReturnCondition condition, @Size(max = 1000) String note,
        Instant maintenanceEndsAt) {
    public ReturnRequest { note = note == null ? null : note.strip(); }
}
