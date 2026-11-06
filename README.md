# EszközPont

Magyar nyelvű egyetemi eszközkatalógus, foglalás- és kölcsönzéskezelő. Spring Boot + Java backend, React + JavaScript frontend, PostgreSQL adatbázis.

## Jelenlegi állapot: törzsadatkezelés és automatikus lejárat

**Elkészült:** regisztráció, JWT-belépés, valódi kijelentkezés/tokenvisszavonás, három szerepkör, eszközkatalógus, adatlap, keresés, kategória/helyszín szűrés, rendezés, lapozás, kezelői teljes leltárlista és admin felhasználólista. Magyar, reszponzív felület. Nyolctáblás Flyway-séma. PostgreSQL-alapú integrációs tesztek és frontendtesztek.

**A foglalási folyamat is elkészült:** igénylés az eszközadatlapról, saját foglalások, kezelői elbírálás, jóváhagyás, indokolt elutasítás és visszavonás, verzióellenőrzés és eseménytörténet. A PostgreSQL-sorzár védi a párhuzamos jóváhagyásokat. Az űrlap budapesti időben kezeli az időpontokat, óraátállításkor elutasítja a kétértelmű és nem létező időpontokat.

**Kölcsönzés:** időablakhoz kötött átadás, jó/használt/sérült állapotú visszavétel, késés és késve visszahozott állapot, kezelői munkalista. Sérüléskor automatikus karbantartási zárolás, kezelői lezárással. A veszélyeztetett későbbi jóváhagyások figyelmeztetést kapnak. Átadás/visszavétel és naplózás közös tranzakcióban, párhuzamos műveletekre is tesztelve.

**Kezelés:** kereshető eszközválasztással kézi karbantartási felvitel, lista/adatlap, lemondás/lezárás és ütközésvédelem. Eszközfelvitel, verziózott szerkesztés, indokolt kivonás és eszköztörténet. Adminisztrátori név/szerep/tiltás módosítás, tokenvisszavonás, párhuzamos kérések mellett is védett utolsó aktív admin.

**Törzsadatok:** adminisztrátori kategória- és tárolásihely-kezelés, keresés, állapotszűrés, lapozás, verziózott szerkesztés, archiválás/visszaaktiválás. Csak eszközhivatkozás nélküli rekord törölhető. Élő igény vagy nyitott kölcsönzés mellett az archiválás is tiltott; a párhuzamos eszközfelvitel és foglalás sem kerülheti meg ezt.

**Automatikus lejárat:** a háttérfolyamat az időszak végén EXPIRED állapotba teszi az át nem vett REQUESTED/APPROVED igényeket, rendszerszintű eseménnyel. Kölcsönzést nem vesz vissza automatikusan. Összesen **104 backendteszt és 39 frontendteszt**.

**Következik:** naptár, időszakra szűrt katalógus és valódi adatokból épülő dashboard. Ezek még tervezett funkciók.

## Tervezési dokumentumok

- [Részletes specifikáció és elfogadási feltételek](docs/specifikacio.md)
- [DTO-k, mezők és validációk](docs/dto-terv.md)
- [REST API és implementáltsági állapot](docs/api-terv.md)
- [Adatmodell és technikai döntések](docs/architektura.md)
- [Az első egység magyarázata és kézi ellenőrzése](docs/elso-fejlesztesi-egyseg.md)
- [Foglalás és elbírálás – működés és ellenőrzés](docs/masodik-fejlesztesi-egyseg.md)
- [Átadás, visszavétel és késések – működés és ellenőrzés](docs/harmadik-fejlesztesi-egyseg.md)
- [Karbantartás, eszköz- és felhasználószerkesztés](docs/kezelesi-fejlesztesi-egyseg.md)
- [Törzsadatok és automatikus foglaláslejárat](docs/torzsadatok-es-lejarat.md)
- [Ellenőrzési jegyzőkönyv](docs/ellenorzes.md)

## Helyi indítás Windows alatt, Docker nélkül

Előfeltétel: JDK 25 vagy újabb, Node.js 22.12+ és pnpm 11.25.0. A mellékelt PowerShell-indító felismeri a JAVA_HOME-ot, az elérhető java/pnpm parancsot, illetve az IntelliJ `.jdks` mappáját és az ezen a gépen rendelkezésre álló Codex Node-futtatókörnyezetet. Nem módosít rendszerszintű beállítást.

A projekt gyökeréből, PowerShellben:

```powershell
# Első alkalommal a frontend függőségei:
.\tools\run.ps1 -Task Install

# Első terminál – PostgreSQL + backend:
.\tools\run.ps1 -Task Backend

# Második terminál – frontend:
.\tools\run.ps1 -Task Frontend
```

Frontend: http://localhost:5173 · Backend: http://localhost:9090 · Swagger: http://localhost:9090/swagger-ui/index.html

A folyamatok Ctrl+C-vel állíthatók le a saját termináljukban. A frontend a /api kéréseket a backendhez továbbítja. Mindkét szolgáltatás szükséges.

A helyi indító **valódi PostgreSQL 18.3-at** futtat 127.0.0.1:55432 címen. Adatok: `.local/postgres`; véletlen JWT-kulcs: `.local/jwt-secret`. Ezek figyelmen kívül hagyott helyi fájlok. Újraindításkor megmaradnak. A local adatbázis fejlesztői, loopbackre kötött, trust-hitelesítésű adatbázis; nem éles üzemre való. Ne adj neki hálózati hozzáférést. A natív PostgreSQL-futtatókönyvtár csak tesztfüggőség, a kiadott alkalmazásba nem kerül bele.

A demófeltöltés kizárólag local profilban, teljesen üres adatbázisnál fut. Meglévő adatokat nem ír felül.

A lejárati háttérfolyamat alapból engedélyezett. Indulás után egy perccel fut először, majd az előző futás befejezése után egy percet vár. Futásonként legfeljebb 100 igényt dolgoz fel. Opcionális környezeti változók: `RESERVATION_EXPIRY_ENABLED=false` kikapcsolja; `RESERVATION_EXPIRY_DELAY=PT1M` az ISO-8601 időközt adja meg. A régebbi, át nem vett demóigények újraindítás után is lejárnak; a történetük megmarad.

| Szerep           | E-mail                 | Helyi demójelszó |
| ---------------- | ---------------------- | ---------------- |
| Hallgató         | anna@eszkozpont.test   | EszkozPont2026!  |
| Második hallgató | bence@eszkozpont.test  | EszkozPont2026!  |
| Eszközfelelős    | kezelo@eszkozpont.test | EszkozPont2026!  |
| Adminisztrátor   | admin@eszkozpont.test  | EszkozPont2026!  |

## Tesztek és kiadható build

```powershell
.\tools\run.ps1 -Task Test
.\tools\run.ps1 -Task Build
```

A backendteszt saját, ideiglenes PostgreSQL-adatbázist hoz létre; nem törli a helyi demó adatait. A tesztek a valódi login végpont JWT-jét használják. A teljes build eredménye `backend/target/eszkozpont-0.1.0-SNAPSHOT.jar`, illetve `frontend/dist`.

Más rendszeren: `backend/./mvnw test`, `backend/./mvnw package`; a frontend könyvtárban `pnpm install --frozen-lockfile`, `pnpm test`, `pnpm build`. A natív PostgreSQL-tesztek támogatják a Windows/Linux/macOS amd64 környezetet. A release célverzió Java 25; a jelenlegi ellenőrzés JDK 26-on történt.

## Normál PostgreSQL / Docker

A `compose.yml` opcionális adatbázis-környezetet ad. A DB_PASSWORD környezeti változó megadása után `docker compose up -d database`. A PostgreSQL-konténer tartós névvel ellátott kötetet használ. Docker ezen a gépen jelenleg nem elérhető, ezért a Compose-indítás itt nincs ellenőrizve.

A backendet ekkor a backend könyvtárból `./mvnw spring-boot:run` indítja; szükséges környezeti változók: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET. Az `.env.example` csak mezőminta: a Spring Boot **nem olvassa automatikusan** a gyökér `.env` fájlját. A változókat a terminálban vagy az IDE futtatási konfigurációjában kell megadni. A local indító ezeket saját maga beállítja a helyi folyamathoz.

Éles profilban nincs demóadat és nincs Swagger. HTTPS-ről és az adott frontend kiszolgálásáról a telepítési környezetnek kell gondoskodnia. Ez a fejlesztési egység nem tartalmaz éles közzétételt.

## Technikai alapok

Java 25 · Spring Boot 4.1.1 · Security Resource Server / HS256 JWT · JPA/Hibernate · MapStruct 1.6.3 · Bean Validation · Flyway · PostgreSQL 18.3 · React 19 · React Router 7 · TanStack Query 5 · Vite 7 · saját CSS.

A Maven parent az órai mintával azonos. A frontend pontos feloldott verziói a pnpm-lock.yaml fájlban szerepelnek. A függőségeket ne frissítsük a védés előtt ellenőrzés nélkül.

Az angol mező- és osztálynevek technikai azonosítók. A termék és a dokumentáció nyelve magyar. A Manrope betűtípus SIL Open Font License alatt helyben kerül kiszolgálásra; licenc: frontend/public/fonts/OFL.txt. A Maven wrapper az Apache Maven hivatalos wrapperje, Apache-2.0 fejléccel. Az eszközillusztrációk saját SVG-komponensek.

Az órai referencia: https://github.com/szabomateunideb/kiadaskezelo_2026_k14, elemzett commit: b8964461e46be34b4f2cf737df48ed7c37c6886f. Az EszközPont önálló domain és implementáció, nem a pénzügyi alkalmazás átnevezése.
