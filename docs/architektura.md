# Adatmodell és technikai döntések

## Kapcsolatok

```mermaid
erDiagram
  APP_USER ||--o{ RESERVATION : igenyel
  APP_USER o|--o{ RESERVATION : elbiral
  EQUIPMENT_CATEGORY ||--o{ EQUIPMENT_ITEM : besorol
  STORAGE_LOCATION ||--o{ EQUIPMENT_ITEM : tarol
  EQUIPMENT_ITEM ||--o{ RESERVATION : foglalas
  RESERVATION ||--o| LOAN : kiadas
  EQUIPMENT_ITEM ||--o{ LOAN : kolcsonzes
  APP_USER ||--o{ LOAN : atad
  APP_USER o|--o{ LOAN : visszavesz
  EQUIPMENT_ITEM ||--o{ MAINTENANCE_PERIOD : karbantartas
  APP_USER ||--o{ MAINTENANCE_PERIOD : rogzit
  LOAN o|--o| MAINTENANCE_PERIOD : serulesi_zar
  APP_USER o|--o{ AUDIT_EVENT : vegrehajt
  EQUIPMENT_ITEM o|--o{ AUDIT_EVENT : eszkozelozmeny
  RESERVATION o|--o{ AUDIT_EVENT : foglalasi_elozmeny
```

A részletes mezőlista és adatbáziskorlátok forrása: backend/src/main/resources/db/migration/V1\_\_initial_schema.sql, illetve a V2\_\_loan_safety_maintenance.sql bővítés. Mind a nyolc üzleti táblához van JPA-entitás. A V2 opcionális, egyedi loan_id kapcsolatot ad a karbantartáshoz: egy sérült visszavétel egy biztonsági zárat hoz létre. A Flyway saját schema history táblája technikai metaadat, nem kilencedik üzleti entitás.

## ADR-001: PostgreSQL és migráció

Az adatok nem veszhetnek el újraindításkor. A Flyway hozza létre a sémát; Hibernate ddl-auto=validate, OSIV=false. Már alkalmazott migrációt nem írunk át: a későbbi módosítás új V2/V3 fájl. Az SQL CHECK-korlátok nem helyettesítik a service réteg üzleti ellenőrzéseit.

## ADR-002: Egyszerű moduláris monolit

Egy backend, funkciónként auth/user/equipment/reservation/loan/maintenance/audit/bootstrap/common csomagok. A dashboard későbbi egység. Nincs generikus CRUD-service, repository-wrapper vagy minden service elé üres interfész. A módosításokat a service tranzakciója fogja össze. Az admin felhasználólistát is service készíti DTO-vá, a controller csak HTTP-adapter. A sérülési karbantartás létrehozását a visszavétellel a LoanService fogja össze; az általános karbantartást, lemondást és minden lezárást az önálló MaintenanceService kezeli.

## ADR-003: DTO és MapStruct

Java record request/response típusok. MapStruct compile-time generált Spring komponens, ReportingPolicy.ERROR. EquipmentMapper kategóriát és helyszínt ténylegesen leképez a válaszba; UserMapper nem tud jelszót visszaadni, mert a válaszban nincs ilyen mező. A kapcsolatok service tranzakción belül, EntityGraph-fal töltődnek. A requestből csak engedélyezett skalármezőket képezünk; jogosultság, tulajdonos és állapot service felelőssége.

## ADR-004: JWT és visszavonás

Spring Security ellenőrzi az aláírást, nem saját kriptográfia. A saját validator ellenőrzi az aktuális fiókot/tokenVersiont és audience-t. Az issuer és időbélyeg-validátor megmarad. HS256-hoz egy külső, legalább32bájtos kulcs. Kijelentkezés atomikus adatbázis-increment; minden korábban kiadott token érvénytelen. Szerepváltás/tiltás ellenőrzése kérésenként történik. Nincs refresh token az első kiadásban. A böngészőfrissítés utáni új belépés ismert UX-kompromisszum.

## ADR-005: PostgreSQL Docker nélkül a fejlesztőgépen

A gépen van JDK, de nincs Docker és nincs elérhető normál PostgreSQL. A tesztfüggőségként használt Zonky 2.2.2 + PostgreSQL18.3 natív bináris valódi PostgreSQL-t indít. A LocalDevelopment test-classpathos indító tartós .local/postgres könyvtárat használ, csak loopbacken. A tesztek ettől független, ideiglenes adatbázist futtatnak. A kiadott jar normál PostgreSQL-kapcsolatot vár; nem tartalmazza a fejlesztői indítót. Nem helyettesítjük H2-vel az adatbázist. A későbbi CI-hoz elérhető Docker esetén Testcontainers is alkalmazható.

## ADR-006: Magyar frontend, angol API

Minden látható termékszöveg magyar. Az enumkódokhoz magyar felirat tartozik. React Router védett útvonalak, AuthContext csak belépési állapothoz, TanStack Query szerveradatokhoz. A query cache kijelentkezéskor törlődik, így következő felhasználó nem látja az előző adatait. A token nem kerül tartós böngészőtárba. A katalógus lifecycle címkéje „Az eszköztárban”; nem ígér még nem ellenőrzött szabad időpontot.

## ADR-007: Az eszközsor a foglalási írások közös zárja

Igénylés, jóváhagyás, elutasítás és visszavonás először PESSIMISTIC_WRITE zárral olvassa az eszközsort. Döntésnél az első lekérdezés csak skalár eszközazonosítót tölt be; a foglalás entitása csak a zár megszerzése után kerül a persistence contextbe. PostgreSQL READ COMMITTED mellett az utána futó ütközésvizsgálat már látja az előző tranzakció végleges döntését. A zár csak azonos eszközön sorosít; a globális zárolás szükségtelen.

Az @Version és a kötelező version requestmező az elavult felületből küldött parancsot is visszautasítja. A [kezdés,befejezés) intervallumok szomszédosak lehetnek; az átfedést startA < endB és endA > startB vizsgálja. A függő igény nem biztosít időszakot. Döntés és audit egy tranzakció; a frontend409 után frissíti a szerveradatokat. A későbbi kölcsönzés/karbantartás írásai ugyanezt az eszközsort kell zárolják, hogy megőrizzék az invariánst. Ez alkalmazásszintű garancia: közvetlen SQL-írás nem kerülheti meg a szolgáltatási szabályokat.

## ADR-008: Budapesti dátumbevitel

A datetime-local értéket explicit Europe/Budapest zónával alakítjuk UTC-vé, függetlenül a böngésző helyi zónájától. A nyári/téli óraátállítás hiányzó vagy kétértelmű perceit visszautasítjuk, és másik időpont megadását kérjük. A backend Instantot tárol, egész percet és időtartamot validál; az API offsetes ISO-8601 értéket fogad. A szerveroldali Clock befecskendezett, így az időhatárok tesztelhetők.

## ADR-009: Kölcsönzés és sérülési zárolás

A tényleges átadás külön Loan rekord, a foglalás APPROVED állapotát nem helyettesíti új, kevert enum. Visszavételkor a Loan lezárul és a foglalás COMPLETED lesz. A Loanhoz nem szükséges külön verzióoszlop, mert minden író parancs az eszközsor zárolása után újraolvassa. Két nyitott kölcsönzést részleges UNIQUE index, két átadást ugyanarra a foglalásra egyedi reservation_id is tilt.

A késés Clockból származtatott, nem tárolt állapot. Visszavétel után a tényleges visszavételi időhöz viszonyítjuk; így a történeti késés nem növekszik. Sérüléskor a visszavétellel és eseményekkel egy tranzakcióban OPEN karbantartás készül, egyedi loan_id kapcsolattal. A tervezett vég nem zárja le automatikusan.

A foglalási listák kölcsönzéseit az oldal azonosítóira csoportosan töltjük be; a veszélyeztetettséget szintén egy csoportos lekérdezés adja. A MapStruct a tényleges kölcsönzési kapcsolatokat is leképezi. A frontend szerveradat-lekérdezései30 másodpercenként frissülnek, a parancsok után azonnal érvénytelenítjük az érintett listákat, adatlapot és eseménytörténetet. A dátummezők input és change eseményeit is kezeljük az ellenőrzött böngészők közötti eltérés miatt.

## ADR-010: Kezelési műveletek és utolsó aktív admin

A karbantartás általános életciklusa önálló MaintenanceService-be került; a korábbi lezárási API szerződése megmaradt. Az EquipmentManagementService az eszközírásokat és a kezelői előzményolvasást fogja össze. Minden eszközt érintő írás ugyanazt az eszközsort zárolja, a teljes változó állapot beolvasása előtt. A requestek nem állíthatnak lifecycle-t vagy döntési metaadatot.

A V3 a felhasználóhoz @Version oszlopot ad. Adminmódosítás és kijelentkezés is növeli a verziót; tényleges adminmódosításkor a tokenVersion is nő. Az utolsó aktív admin invariánsát a felhasználó-adminisztrációs parancsok közös,173528/1 kulcsú pg_advisory_xact_lock zára védi. A zárolás után frissen ellenőrizzük az eljáró admin jogát és az aktív adminok számát, majd sorzárral védett felhasználót módosítunk. A zár tranzakcióvégig él és automatikusan feloldódik. A szerepet létrehozó/módosító jövőbeli alkalmazásútvonalaknak is ezt kell használniuk; közvetlen SQL nincs az alkalmazás műveletei között.

A kliens szerkesztés közben megőrzi a kezdeti verziót. Konfliktusnál a piszkozat megmarad; friss adatok betöltése explicit űrlap-visszaállítás. Saját felhasználó-módosítás után a kliens munkamenetét és query cache-ét is töröljük. A felhasználócélt kezelő általános auditbővítés külön feladat lenne; jelenleg az audit eszköz/foglalás alapú.

## Források

- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-boot/how-to/data-initialization.html
- https://mapstruct.org/documentation/stable/reference/html/
- https://github.com/zonkyio/embedded-postgres
- https://www.postgresql.org/docs/current/explicit-locking.html
- https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
- https://www.postgresql.org/docs/current/transaction-iso.html
- https://www.postgresql.org/docs/current/indexes-partial.html
- https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS
