package hu.eszkozpont.maintenance;

import hu.eszkozpont.common.AuditedEntity;
import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.loan.Loan;
import hu.eszkozpont.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "maintenance_period")
@Getter
@Setter
public class MaintenancePeriod extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private EquipmentItem equipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", unique = true)
    private Loan loan;
    @Column(nullable = false) private Instant startsAt;
    @Column(nullable = false) private Instant endsAt;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;
    private Instant closedAt;
}
