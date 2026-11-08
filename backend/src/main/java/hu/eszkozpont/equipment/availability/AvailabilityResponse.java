package hu.eszkozpont.equipment.availability;

import java.time.Instant;
import java.util.List;

public record AvailabilityResponse(long equipmentId, Instant from, Instant to, Instant checkedAt,
        List<BlockingInterval> blockingIntervals, boolean overdueLoan, boolean overdueMaintenance) {}
