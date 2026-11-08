package hu.eszkozpont.reservation;

import hu.eszkozpont.common.ApiException;
import org.springframework.http.HttpStatus;
import java.time.*;

/** Az igénylés és a szabad eszközök keresése ugyanazt az időablakot fogadja el. */
public final class ReservationPeriods {
    private ReservationPeriods() {}

    public static void validate(Instant start, Instant end, Instant now) {
        if (start == null || end == null) throw invalid("Az átvétel és visszahozás időpontját együtt add meg.");
        if (!start.isAfter(now) || start.isAfter(now.plus(Duration.ofDays(60)))) {
            throw invalid("Az átvétel jövőbeli, legfeljebb 60 nappal későbbi időpont legyen.");
        }
        var duration = Duration.between(start, end);
        if (duration.compareTo(Duration.ofHours(1)) < 0 || duration.compareTo(Duration.ofDays(7)) > 0) {
            throw invalid("A foglalás időtartama legalább 1 óra és legfeljebb 7 nap lehet.");
        }
        if (start.getNano() != 0 || end.getNano() != 0 || start.getEpochSecond() % 60 != 0 || end.getEpochSecond() % 60 != 0) {
            throw invalid("Az időpontokat egész percre pontosan add meg.");
        }
    }

    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD", message); }
}
