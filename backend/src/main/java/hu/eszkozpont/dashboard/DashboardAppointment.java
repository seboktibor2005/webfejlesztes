package hu.eszkozpont.dashboard;

import java.time.Instant;

public record DashboardAppointment(long reservationId, String equipmentName, String inventoryCode, Instant at) {}
