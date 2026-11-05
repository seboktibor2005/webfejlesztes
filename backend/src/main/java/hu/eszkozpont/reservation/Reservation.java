package hu.eszkozpont.reservation;

import hu.eszkozpont.common.AuditedEntity;
import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "reservation")
@Getter
@Setter
public class Reservation extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private EquipmentItem equipment;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Column(nullable = false, length = 500)
    private String purpose;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.REQUESTED;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private AppUser decidedBy;
    private Instant decidedAt;
    @Column(length = 500)
    private String decisionReason;
    @Version
    private long version;
}
