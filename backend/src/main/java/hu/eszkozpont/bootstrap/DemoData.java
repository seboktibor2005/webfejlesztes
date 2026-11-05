package hu.eszkozpont.bootstrap;

import hu.eszkozpont.equipment.*;
import hu.eszkozpont.user.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@Profile("local")
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoData implements CommandLineRunner {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final LocationRepository locations;
    private final EquipmentRepository equipment;
    private final PasswordEncoder passwords;
    public DemoData(UserRepository users, CategoryRepository categories, LocationRepository locations, EquipmentRepository equipment, PasswordEncoder passwords) {
        this.users = users; this.categories = categories; this.locations = locations; this.equipment = equipment; this.passwords = passwords;
    }

    @Override @Transactional
    public void run(String... args) {
        // Csak teljesen üres adatbázist töltünk; meglévő felhasználói adatokat nem írunk felül.
        if (users.count() > 0 || categories.count() > 0 || equipment.count() > 0 || locations.count() > 0) return;
        for (var entry : List.of(new String[]{"anna@eszkozpont.test", "Kovács Anna", "USER"},
                new String[]{"bence@eszkozpont.test", "Szabó Bence", "USER"},
                new String[]{"kezelo@eszkozpont.test", "Nagy Dóra", "MANAGER"},
                new String[]{"admin@eszkozpont.test", "Rendszergazda", "ADMIN"})) {
            var user = new AppUser(); user.setEmail(entry[0]); user.setDisplayName(entry[1]);
            user.setRole(Role.valueOf(entry[2])); user.setPasswordHash(passwords.encode("EszkozPont2026!")); users.save(user);
        }
        var camera = category("Fotó és videó", "Kamerák, objektívek és a történetmesélés eszközei.");
        var audio = category("Hangtechnika", "Tiszta hang felvételhez és előadáshoz.");
        var presentation = category("Prezentáció", "Eszközök ötleteid bemutatásához.");
        var development = category("Fejlesztés", "Prototípusokhoz és kísérletezéshez.");
        var media = location("MEDIA", "Médiaműhely", "Informatikai épület", "M.12");
        var lab = location("LAB", "Innovációs labor", "Informatikai épület", "I.204");
        item("FOTO-001", "Canon EOS R50", "Kompakt, tükör nélküli kamera 18–45 mm-es objektívvel. Interjúhoz, rövidfilmhez és projektfotózáshoz.", camera, media);
        item("FOTO-002", "Sony Alpha 6400", "Sokoldalú kamera 16–50 mm-es objektívvel és hordtáskával. Videós és fotós egyetemi projektekhez.", camera, media);
        item("HANG-001", "RØDE Wireless GO II", "Kétcsatornás vezeték nélküli mikrofonkészlet. Interjúkhoz, riporthoz és prezentációkhoz.", audio, media);
        item("HANG-002", "Zoom H5 hangrögzítő", "Hordozható sztereó hangrögzítő cserélhető mikrofonkapszulával. Terepi felvételekhez.", audio, media);
        item("PREZ-001", "Epson EB-FH52 projektor", "Full HD projektor HDMI-csatlakozóval. Előadások, bemutatók és közös vetítések eszköze.", presentation, lab);
        item("PREZ-002", "Logitech Spotlight", "Vezeték nélküli prezentációs távirányító USB-vevőegységgel és töltőkábellel.", presentation, lab);
        item("FEJL-001", "Arduino Starter Kit", "Arduino Uno alaplap, próbapanel, érzékelők és alkatrészek. Interaktív prototípusokhoz.", development, lab);
        item("FEJL-002", "Raspberry Pi 5 készlet", "8 GB memóriás Raspberry Pi 5 házzal, tápegységgel és microSD-kártyával.", development, lab);
        var retired = item("FOTO-003", "Canon EOS 600D", "Korábbi oktatási kamera, a kölcsönzésből kivonva.", camera, media);
        retired.setLifecycle(EquipmentLifecycle.RETIRED);
    }
    private EquipmentCategory category(String name, String description) {
        var c = new EquipmentCategory(); c.setName(name); c.setDescription(description); return categories.save(c);
    }
    private StorageLocation location(String code, String name, String building, String room) {
        var l = new StorageLocation(); l.setCode(code); l.setName(name); l.setBuilding(building); l.setRoom(room); return locations.save(l);
    }
    private EquipmentItem item(String code, String name, String description, EquipmentCategory category, StorageLocation location) {
        var e = new EquipmentItem(); e.setInventoryCode(code); e.setName(name); e.setDescription(description);
        e.setCategory(category); e.setLocation(location); return equipment.save(e);
    }
}
