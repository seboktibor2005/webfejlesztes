package hu.eszkozpont.equipment;

import hu.eszkozpont.common.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "equipment_item") @Getter @Setter
public class EquipmentItem extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String inventoryCode;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 2000) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false)
    private EquipmentCategory category;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EquipmentLifecycle lifecycle = EquipmentLifecycle.ACTIVE;
    @Version private long version;
}
