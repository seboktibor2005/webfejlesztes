# Első fejlesztési egység – mit és miért?

## Fájlok

- backend/pom.xml: rögzített Java/Spring-függőségek és MapStruct-fordítógenerálás.
- backend/src/main/resources/application.yml: normál PostgreSQL, környezeti titkok, OSIV kikapcsolva, sémavalidálás.
- backend/src/main/resources/application-local.yml: helyi demó és Swagger.
- backend/src/main/resources/db/migration/V1\_\_initial_schema.sql: mind a nyolc tábla, FK/UNIQUE/CHECK és indexek.
- backend/src/main/java/hu/eszkozpont/auth/: validált kérésobjektumok, BCrypt, JWT kiadás és ellenőrzés, logout.
- backend/src/main/java/hu/eszkozpont/user/: felhasználó-entitás, repository, mapper és biztonságos válaszok.
- backend/src/main/java/hu/eszkozpont/equipment/: JPA-kapcsolatok, tényleges MapStruct, keresés, fehérlistás rendezés, katalógus REST.
- backend/src/main/java/hu/eszkozpont/common/: időbélyegek, lapozott válasz, magyar ProblemDetail hibák.
- backend/src/main/java/hu/eszkozpont/bootstrap/DemoData.java: tranzakcionális, kizárólag üres local adatbázis feltöltése.
- backend/src/test/java/hu/eszkozpont/: valódi adatbázisos tesztek és külön helyi indító.
- frontend/src/api/client.js: egy helyen kezelt API-kérések és hibaformátum.
- frontend/src/auth/AuthContext.jsx: memóriabeli token és kijelentkezéskor adattörlés a cache-ből.
- frontend/src/pages/: valódi API-val működő magyar oldalak.
- frontend/src/components/: közös keret, visszajelzések, saját eszközillusztrációk.
- frontend/src/styles.css: közös színek/tipográfia, asztali és mobilnézet, csökkentett mozgás támogatása.
- tools/run.ps1: kényelmes helyi indítás és ellenőrzés.

## Kézi ellenőrzés

1. A README alapján indítsd a backendet és a frontendet.
2. Nyisd meg a http://localhost:5173 címet. Bejelentkezés nélkül a belépőoldal látszik.
3. Anna demófiókjával lépj be. Nyolc aktív eszköz található; a második oldalt is próbáld ki.
4. Keress „canon”-ra; egy aktív találat. Próbálj „nincsilyen” keresést; üres állapot és szűrőtörlés jelenik meg.
5. Válassz kategóriát és helyszínt, változtass rendezést; nyiss eszközadatlapot.
6. USER-ként nyisd meg a /felhasznalok vagy /kezeles útvonalat; tiltóoldalt kell kapnod. A backend közvetlenül is 403-at ad.
7. Jelentkezz ki, majd kezelővel lépj be. Az Eszközállomány kilenc rekordot mutat, köztük a kivont Canon EOS 600D-t.
8. Adminnal a Felhasználók oldalon a fiókok és szerepeik láthatók, jelszó nélkül.
9. Regisztrálj külön tesztcímmel. USER szerepet kapsz, és belépés után használhatod a katalógust.
10. Indítsd újra a backendet: a regisztrált fiók megmarad; a bemutatóadatok nem duplázódnak.
11. Szűkítsd az ablakot mobilméretre: a navigáció menübe kerül, a katalógus alkalmazkodik.
12. Futtasd a `tools/run.ps1 -Task Test` ellenőrzést.

## Amit ebből érdemes megérteni a védésre

- A Controller HTTP-adapter, az AuthService/EquipmentService az alkalmazási logika, a repository adatbázist ér el.
- Az entitás nem API-szerződés. Az adatlap nested DTO-it a MapStruct készíti, nem a Jackson JPA-szerializáció.
- Egy elrejtett menüpont nem jogosultságvédelem: a SecurityFilterChain és a metódusvédelmek is elutasítanak.
- A JWT aláírás ellenőrzése nem ugyanaz, mint a token visszavonása. Erre külön tokenVersion-ellenőrzés van.
- Az előzetes e-mail-ellenőrzés a jó hibaüzenethez kell; a UNIQUE kulcs a párhuzamos regisztrációt is védi.
- A lifecycle nem foglaltság. A foglalási modult a következő egység adja hozzá, külön állapotgéppel és időintervallum-védelemmel.
