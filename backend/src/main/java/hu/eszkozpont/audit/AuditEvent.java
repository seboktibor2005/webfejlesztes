package hu.eszkozpont.audit;

import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.reservation.Reservation;
import hu.eszkozpont.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "audit_event")
@Getter
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "equipment_id")
    private EquipmentItem equipment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id")
    private Reservation reservation;
    @Column(nullable = false, length = 40)
    private String eventType;
    @Column(length = 30) private String fromState;
    @Column(length = 30) private String toState;
    @Column(length = 1000) private String note;
    @Column(nullable = false) private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(Reservation reservation, AppUser actor, String eventType,
            String fromState, String toState, String note, Instant occurredAt) {
        this.reservation = reservation;
        this.equipment = reservation.getEquipment();
        this.actor = actor;
        this.eventType = eventType;
        this.fromState = fromState;
        this.toState = toState;
        this.note = note;
        this.occurredAt = occurredAt;
    }
}
