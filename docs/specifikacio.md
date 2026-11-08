# EszközPont – részletes specifikáció

Állapot: elfogadott termékkoncepció, 1. specifikáció. Dátum: 2026. november 5.

## Nyelv és cél

A felület, a visszajelzések, a dokumentáció és a védési anyag magyar. A kódbeli azonosítók, adatbázisnevek, JSON-mezők és útvonalak angolok. A felhasználó magyarul és angolul is adhat utasításokat; ettől a termék nyelve nem változik.

Egy intézmény konkrét fizikai eszközeinek foglalása, elbírálása, átadása és visszavétele. Egy foglalás egy eszközpéldányra vonatkozik. Nem kezelünk fizetést, intézmények közötti adatmegosztást, e-mailt, fájlfeltöltést vagy ismétlődő foglalást.

## Követelmények és elfogadási feltételek

| ID       | Követelmény         | Elfogadási feltétel                                                                                                                                                                                                                |
| -------- | ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AUTH-01  | Regisztráció        | Érvényes adatokkal USER készül; a jelszó hash; e-mail kisbetűs; duplikáció 409; beküldött role 400.                                                                                                                                |
| AUTH-02  | Bejelentkezés       | Helyes adatokkal 30 perces JWT; rossz jelszó és tiltott fiók egységes 401.                                                                                                                                                         |
| AUTH-03  | Visszavonás         | Kijelentkezés minden korábbi JWT-t érvénytelenít; böngészőfrissítés után új belépés kell.                                                                                                                                          |
| AUTH-04  | Három szerepkör     | USER nem érhet el management/admin végpontot; MANAGER nem adminisztrálhat felhasználót.                                                                                                                                            |
| CAT-01   | Katalógus           | Csak aktív eszköz, aktív kategória és helyszín szerepel; keresés névre/leltári számra; kategória/hely szűrés; rendezés; lapozás.                                                                                                   |
| CAT-02   | Adatlap             | Kategória, leírás, leltári szám és tárolási hely látható; nincs más felhasználó személyes adata.                                                                                                                                   |
| RES-01   | Igénylés            | 1 óra–7 nap időtartam, legfeljebb 60 nappal előre, egész perces időpontok, jövőbeli kezdet, 10–500 karakteres cél.                                                                                                                 |
| RES-02   | Függő igények       | A REQUESTED igények átfedhetnek; a benyújtás nem biztosítja az eszközt.                                                                                                                                                            |
| RES-03   | Jóváhagyás          | Kezelő dönt, saját igényéről nem; időütközés/karbantartás esetén 409. Két párhuzamos ütköző jóváhagyásból egy sikerülhet.                                                                                                          |
| RES-04   | Elutasítás          | Csak REQUESTED igényre, kötelező 5–500 karakteres indokkal.                                                                                                                                                                        |
| RES-05   | Visszavonás         | Saját REQUESTED/APPROVED igény visszavonható a kezdés és az átadás előtt. Kezelő másét indokkal, átadás előtt mondhatja le.                                                                                                        |
| RES-06   | Adatvédelem         | USER csak saját foglalás részleteit látja; idegen azonosítóra 404. Foglaltság mások neve/célja nélkül.                                                                                                                             |
| RES-07   | Automatikus lejárat | REQUESTED/APPROVED, endsAt <= most és semmilyen kölcsönzés nincs: EXPIRED és egy rendszeresemény, közös tranzakcióban. Ismételt feldolgozás nem dupláz.                                                                            |
| LOAN-01  | Átadás              | APPROVED igény, startsAt <= most < endsAt, nincs nyitott kölcsönzés vagy aktív karbantartás, aktív eszköz és foglaló; állapot GOOD/WORN.                                                                                           |
| LOAN-02  | Visszavétel         | Nyitott kölcsönzés zárható; foglalás COMPLETED lesz; visszavétel rögzíti a kezelőt, időpontot és állapotot.                                                                                                                        |
| LOAN-03  | Sérülés             | DAMAGED visszaadásnál kötelező indok és karbantartási zár; ha későbbi foglalást érint, az megmarad, de veszélyeztetettként jelezzük. Átadás mindenképp tiltott.                                                                    |
| LOAN-04  | Késés               | returnedAt null és dueAt < most; nem lesz automatikusan visszavett. Lejárt nyitott kölcsönzés alatt új jóváhagyás tiltott.                                                                                                         |
| MAINT-01 | Karbantartás        | OPEN időszak blokkol; átfedő jóváhagyás esetén kézi felvétel 409, előbb rendezni kell az igényeket. Sérült visszaadás biztonsági zára kivétel.                                                                                     |
| MAINT-02 | Túlfutás            | Ha OPEN és a kezdete elmúlt, az eszköz átadása a tervezett vég után is tiltott a lezárásig. Új jóváhagyás is tiltott túlfutás alatt.                                                                                               |
| ADMIN-01 | Leltár              | Kezelő létrehozhat/módosíthat eszközt; előzménnyel kivonás, nem törlés. Nyitott kölcsönzés/futó jövőbeli igény esetén kivonás 409.                                                                                                 |
| ADMIN-02 | Törzsadat           | Admin felvesz/szerkeszt/archivál/visszaaktivál, kötelező verzióval. Hivatkozott rekord törlése 409; élő igény vagy nyitott kölcsönzés mellett archiválás 409. Archivált kategória/hely eszközére nincs új igény/jóváhagyás/átadás. |
| ADMIN-03 | Fiókok              | Admin szerepet/engedélyezettséget módosíthat, tokenverzió növelésével. Utolsó aktív admin védett, párhuzamos módosítás mellett is.                                                                                                 |
| EXTRA-01 | Naptár              | Heti foglaltság, személyes adatok nélkül; nem állít elérhetőséget pusztán a lifecycle alapján.                                                                                                                                     |
| EXTRA-02 | Dashboard           | Valódi lekérdezésekből szerepkörhöz illő teendők és 30 napos kategóriánkénti kölcsönzésszám.                                                                                                                                       |
| EXTRA-03 | Események           | Minden foglalási/átadási művelet ugyanabban a tranzakcióban naplózódik; csak hozzáfűzés, hozzáférés a foglaláséhoz igazodik.                                                                                                       |

## Állapotátmenetek

| Kiindulás                                      | Művelet  | Eredmény                | Ki?                                           |
| ---------------------------------------------- | -------- | ----------------------- | --------------------------------------------- |
| REQUESTED                                      | approve  | APPROVED                | Másik MANAGER/ADMIN                           |
| REQUESTED                                      | reject   | REJECTED                | MANAGER/ADMIN, indok                          |
| REQUESTED                                      | cancel   | CANCELLED               | Tulajdonos vagy kezelő                        |
| APPROVED, nincs kölcsönzés                     | cancel   | CANCELLED               | Tulajdonos kezdés előtt, vagy kezelő indokkal |
| APPROVED                                       | checkout | APPROVED + nyitott loan | MANAGER/ADMIN                                 |
| APPROVED + nyitott loan                        | return   | COMPLETED + lezárt loan | MANAGER/ADMIN                                 |
| REQUESTED/APPROVED, nincs loan, endsAt <= most | expire   | EXPIRED                 | Háttérfeldolgozó                              |

A végállapotok nem nyithatók újra. A kész üzleti parancs ismétlése 409; nem jön létre második kölcsönzés vagy visszavétel. A kliens letiltja a futó parancs gombját. Az expire feladat ismételten biztonságosan fut. A foglalási időszakot benyújtás után nem szerkesztjük: visszavonás és új igény szükséges.

## Idő- és versenyhelyzet-szabályok

UTC/Instant tárolás, Europe/Budapest megjelenítés, ISO-8601 időpont explicit offsettel. Az intervallum [startsAt, endsAt): a szomszédos foglalások nem ütköznek. Átfedés: a.start < b.end és a.end > b.start. Jelen idő injektált Clockból.

Az egy eszközt érintő foglalási, kölcsönzési és karbantartási írás először equipment_item sort zárol, majd a változó üzleti állapotot olvassa és ellenőrzi. A lejárati parancs ugyanezt a sorrendet követi. A törzsadatírások kizáró tranzakciós advisory zárat, az eszközfelvitel/-szerkesztés/-kivonás ennek megosztott párját kéri az eszközsor zárolása előtt. Archiváláskor az összes kapcsolt eszközsort növekvő id szerint zároljuk; így a kapcsolatok és az élő használat ellenőrzése közben sem kerülhet be új eszköz vagy foglalás.

A várakozás után friss állapotból ellenőrzünk. A @Version önmagában nem védi a két új rekord közötti ütközést. A jóváhagyott időszakokat a service sorzárral védi; közvetlen SQL-írás nem megengedett alkalmazásútvonal. Nyitott kölcsönzésre PostgreSQL részleges UNIQUE index is van.

Foglalás felvételekor is ellenőrizzük az akkor ismert akadályokat; jóváhagyáskor és átadáskor újból. A később késedelmesen visszahozott eszköz jövőbeli jóváhagyása nem szűnik meg automatikusan, de az átadás blokkolt és a kezelő figyelmeztetést kap.

## Biztonság

Bearer JWT a frontend memóriájában; nincs localStorage, refresh token, hitelesítő cookie, Basic vagy form login. HS256 rögzített algoritmus, legalább 32 bájtos környezeti kulcs, issuer/audience/exp/nbf/sub/role/tokenVersion ellenőrzés. Védett kérésenként aktív fiók és aktuális tokenverzió ellenőrzése adatbázisból. Kijelentkezés minden eszközön érvénytelenít. Nem adatbázisfüggetlen hitelesítés.

Jelszó 12–64 karakter, legfeljebb 72 UTF-8 bájt BCrypt miatt. Nincs visszaadott hash vagy jelszót naplózó DTO. A kliens által nem módosítható mezők ismeretlen mezőként 400-at adnak. Éles környezetben HTTPS, pontos CORS-origin; Swagger és demóadat csak local profilban.

## Architektúra és minőség

Java 25, Spring Boot 4.1.1 (az órai verzióval egyező rögzítés), MVC, Security Resource Server, JPA, MapStruct 1.6.3, Bean Validation, Flyway, PostgreSQL. JDK 26-ról release 25-re is fordítható. React + JavaScript + Vite + Router + TanStack Query, saját CSS. Funkciónkénti csomagolás, azon belül Controller–Service–Repository. Entitás nem kerül az API-válaszba; OSIV kikapcsolva.

Az első egység tesztjei Docker nélkül, natív PostgreSQL 18.3-mal futnak (Zonky embedded-postgres, kizárólag test függőség). A helyi fejlesztőindító ugyanígy futtat valódi, tartós PostgreSQL-t. Ez környezeti döntés: a gépen nincs elérhető Docker; a végleges alkalmazás normál PostgreSQL-hez és a mellékelt Compose-környezethez is csatlakozik. H2-t nem használunk. Testcontainers később Dockerrel választható; nem kell második tesztadatbázist fenntartani.

## Fejlesztési egységek

1. **Futó alap:** nyolctáblás séma, JWT, RBAC, regisztráció/belépés/kijelentkezés, katalógus/adatlap/szűrés/lapozás, kezelői leltárlista, admin felhasználólista, magyar felület és integrációs tesztek.
2. **Foglalási folyamat:** létrehozás, saját lista, elbírálás, eseménytörténet, párhuzamos jóváhagyás tesztje.
3. **Kölcsönzés és kezelés:** átadás/visszavétel, karbantartás, eszköz- és felhasználómódosítás.
4. **Extrák és védés:** naptár, dashboard, teljes folyamat tesztje, README, próbabemutató.

A megvalósítás az alapokat, a foglalási/kölcsönzési folyamatot, a kezelési funkciókat és az adminisztrátori törzsadatkezelést tartalmazza. A párhuzamos adminmódosítások mellett is megmarad egy aktív admin. A kategória/helyszín felvitele, szerkesztése, archiválása, visszaaktiválása és hivatkozás nélküli törlése elkészült. Az átvétel nélkül véget ért igények automatikus lejárati feldolgozója működik, rendszereseménnyel és párhuzamossági védelemmel.

Az időszakra szűrt katalógus és az eszközadatlap heti foglaltsági naptára elkészült. A naptár csak az aktuális APPROVED/OPEN akadályokat mutatja, személyes adatok nélkül; a múltbeli napokat tartalmazó hét nem teljes történeti napló. A késedelmes kölcsönzés és túlfutott karbantartás külön figyelmeztetést ad. Az időszakszűrés adatbázisban, lapozás előtt történik, az igényléssel közös időablak-validációval. A keresett időpontok a foglalóűrlapba továbbkerülnek. Az olvasás pillanatkép, az igénylés/jóváhagyás külön újraellenőriz. A dashboard még tervezett; szerződésterve az API-tervben külön szerepel.
