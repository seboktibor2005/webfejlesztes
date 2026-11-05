package hu.eszkozpont.maintenance;

import jakarta.validation.constraints.Size;

public record MaintenanceCloseRequest(@Size(max = 500) String note) {
    public MaintenanceCloseRequest { note = note == null ? null : note.strip(); }
}
