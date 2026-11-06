package hu.eszkozpont;

import hu.eszkozpont.reservation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class ReservationExpiryJobTest {
    @Test void aFailedReservationDoesNotStopTheRemainingBatch() {
        var service = mock(ReservationExpiryService.class);
        when(service.candidates()).thenReturn(List.of(1L, 2L));
        when(service.expire(1L)).thenThrow(new IllegalStateException("Mesterséges teszthiba"));
        new ReservationExpiryJob(service).run();
        verify(service).expire(2L);
    }
}
