package hu.eszkozpont.reservation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
@ConditionalOnProperty(name = "app.reservations.expiry-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryJob {
    private final ReservationExpiryService service;
    public ReservationExpiryJob(ReservationExpiryService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${app.reservations.expiry-delay:PT1M}", initialDelayString = "${app.reservations.expiry-delay:PT1M}")
    public void run() {
        for (long id : service.candidates()) {
            try { service.expire(id); }
            catch (RuntimeException exception) { log.error("A foglalás automatikus lejáratkezelése sikertelen, azonosító: {}", id, exception); }
        }
    }
}
