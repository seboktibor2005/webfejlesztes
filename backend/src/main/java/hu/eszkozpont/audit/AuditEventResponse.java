package hu.eszkozpont.audit;

import hu.eszkozpont.user.UserSummary;
import java.time.Instant;

public record AuditEventResponse(Long id, String eventType, UserSummary actor,
        String fromState, String toState, String note, Instant occurredAt) {}
