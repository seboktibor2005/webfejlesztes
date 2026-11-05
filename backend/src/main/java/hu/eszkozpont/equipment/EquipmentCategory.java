package hu.eszkozpont.equipment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "equipment_category") @Getter @Setter
public class EquipmentCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 80) private String name;
    @Column(length = 500) private String description;
    @Column(nullable = false) private boolean active = true;
}
