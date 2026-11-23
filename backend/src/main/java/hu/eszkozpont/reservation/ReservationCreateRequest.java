package hu.eszkozpont.reservation;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record ReservationCreateRequest(
        @NotNull(message = "Válassz eszközt.") @Positive(message = "Érvénytelen eszközazonosító.") Long equipmentId,
        @NotNull(message = "Add meg az átvétel időpontját.") Instant startsAt,
        @NotNull(message = "Add meg a visszahozás időpontját.") Instant endsAt,
        @NotBlank(message = "Írd le a felhasználás célját.")
        @Size(min = 1, max = 500, message = "A cél leírása 1–500 karakter legyen.") String purpose) {
    public ReservationCreateRequest {
        if (purpose != null) purpose = purpose.strip();
    }
}
