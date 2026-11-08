package hu.eszkozpont.equipment.availability;

import java.time.Instant;

/** Tudatosan nem tartalmaz foglalásazonosítót, felhasználót vagy megjegyzést. */
public record BlockingInterval(Instant startsAt, Instant endsAt, String kind) {}
