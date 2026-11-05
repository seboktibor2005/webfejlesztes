package hu.eszkozpont.equipment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "storage_location") @Getter @Setter
public class StorageLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 100) private String building;
    @Column(nullable = false, length = 30) private String room;
    @Column(nullable = false) private boolean active = true;
}
