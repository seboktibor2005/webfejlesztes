package hu.eszkozpont.equipment;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ReferenceDataLock {
    private final EntityManager entityManager;
    public ReferenceDataLock(EntityManager entityManager) { this.entityManager = entityManager; }

    // Az eszközkapcsolatok írásai együtt futhatnak, de törzsadat-módosítással nem.
    public void shared() {
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock_shared(173528, 2)").getSingleResult();
    }

    public void exclusive() {
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(173528, 2)").getSingleResult();
    }
}
