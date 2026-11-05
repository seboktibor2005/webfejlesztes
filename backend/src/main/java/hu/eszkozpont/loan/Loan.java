package hu.eszkozpont.loan;

import hu.eszkozpont.equipment.EquipmentItem;
import hu.eszkozpont.maintenance.MaintenancePeriod;
import hu.eszkozpont.reservation.Reservation;
import hu.eszkozpont.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "loan")
@Getter
@Setter
public class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private EquipmentItem equipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false)
    private AppUser issuedBy;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant dueAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "returned_by")
    private AppUser returnedBy;
    private Instant returnedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private IssueCondition issueCondition;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private ReturnCondition returnCondition;
    @Column(length = 1000) private String returnNote;
    @OneToOne(mappedBy = "loan", fetch = FetchType.LAZY)
    private MaintenancePeriod maintenance;
}
