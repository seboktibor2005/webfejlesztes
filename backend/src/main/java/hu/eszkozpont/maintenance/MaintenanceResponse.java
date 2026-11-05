package hu.eszkozpont.maintenance;

import java.time.Instant;

public record MaintenanceResponse(Long id, Instant startsAt, Instant endsAt, String reason,
        MaintenanceStatus status, Instant closedAt) {}
