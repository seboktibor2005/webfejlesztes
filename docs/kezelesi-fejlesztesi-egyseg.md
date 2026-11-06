# Karbantartás, eszköz- és felhasználószerkesztés

## Elkészült működés

Az eszközfelelős és az admin a Karbantartások listában állapotra szűrhet, lapozhat, eszközadatlapról egy konkrét eszköz előzményeire szűkíthet. A rendezés kezdési idő/id szerint stabil. A lista és az adatlap30 másodpercenként frissül. A sérült visszavételből keletkezett karbantartás is megjelenik, a kapcsolódó foglalásra mutató hivatkozással.

Új karbantartáshoz név/leltári szám alapján kereshető eszközválasztó tartozik. A maximum20 találat kereséssel szűkíthető, a kivont eszköz nem választható. Az időpontok budapesti idő szerint adhatók meg. Már elkezdett javítás is rögzíthető, de a vége jövőbeli legyen, az időpontok egész percre essenek, és a kezdete előzze meg a végét. A trimelt indok5–500 karakter.

A kézi felvitel ugyanazt az eszközsort zárolja, mint a foglalás. Élő jóváhagyott foglalás, nyitott kölcsönzéssel átfedő időszak, késedelmesen kint lévő eszköz vagy blokkoló OPEN karbantartás409-et ad. Egy függő igény önmagában nem akadály; jóváhagyásakor már ellenőrizzük a karbantartást. Az egymáshoz pontosan csatlakozó időszakok megengedettek. A vissza nem hozott eszköz pontosan a határidőnél sem tekinthető már bent lévőnek.

Csak még el nem kezdett OPEN karbantartás mondható le, kötelező5–500 karakteres indokkal. Elkezdett javítást lemondás helyett a javítás ellenőrzése után kell lezárni. A tervezett befejezés nem oldja fel a zárolást. A létrehozás, lemondás és lezárás ugyanabban a tranzakcióban eseményt is ír. A sérült visszavétel biztonsági zára változatlanul létrejöhet későbbi jóváhagyás mellett; ez szükséges kivétel.

## Eszköznyilvántartás

Az Eszközállományból új eszköz vehető fel, és az aktív/kivont eszközök szerkesztőlapja is megnyitható. A leltári szám trimelt, nagybetűs, egyedi, legfeljebb40 karakteres ASCII kód; betű/szám/kötőjel/aláhúzás engedélyezett, az első karakter betű/szám. Létrehozás után nem változik. A név max120, a leírás max2000 karakter, a kategória és helyszín aktív, létező rekord.

A PUT és a kivonási parancs a foglalásból már ismert version-mezővel dolgozik. Elavult képernyő409 STALE_VERSION választ kap. A felület megtartja a piszkozatot; a friss adatok betöltése külön gomb, amely visszaállítja az űrlapot. Kint lévő eszköz tárolási helye nem változtatható, de neve/leírása pontosítható.

Az eszközt nem töröljük. Kivonás csak ACTIVE állapotból, indokkal, nyitott kölcsönzés és még élő REQUESTED/APPROVED igény nélkül lehetséges. Élő igény: endsAt > most. A már lejárt, át nem vett igény nem blokkolja a kivonást; az automatikus EXPIRED feldolgozó külön későbbi feladat. A kivont eszköz a hallgatói katalógusból eltűnik, kezelői adatlapja és előzményei megmaradnak. Az eszköz teljes eseménytörténetét csak kezelő/admin láthatja.

MapStruct képezi a requestek engedélyezett skalármezőit és a kapcsolt response DTO-kat. A service oldja fel az FK-kat, ellenőrzi a verziót és a jogosultságot. Nincs tetszőleges JPA-entitásfrissítés vagy kézzel beküldhető lifecycle.

## Felhasználók és az utolsó admin védelme

Csak ADMIN szerkeszthet felhasználót: megjelenített név, szerepkör, aktív/letiltott állapot. E-mail és jelszó ezen az útvonalon nem módosítható. Minden mező és az aktuális version kötelező. A kliens mentés előtt áttekintést mutat. Minden tényleges változás növeli a tokenVersiont és visszavonja az érintett korábbi tokenjeit. Változatlan mentés nem. Újraengedélyezés után sem éled fel a régi token.

Az adminszám ellenőrzése önmagában versenyhelyzetet engedne: két admin egyszerre láthatna még egy másikat. Ezért a felhasználó-módosítások az első userentitás betöltése előtt PostgreSQL tranzakciós advisory zárat szereznek, az alkalmazás rögzített173528/1 névterében. A zár a tranzakció végén automatikusan feloldódik. Utána a célfelhasználót sorzárral olvassuk, az eljáró admin szerepét/aktivitását pedig ismét ellenőrizzük. A friss adminszám alapján az utolsó aktív ADMIN letiltása vagy lefokozása409 LAST_ADMIN. A várakozás közben lefokozott admin403-at kap, akkor is, ha a tokenje a kérés elején még érvényes volt.

A közös zár az alacsony forgalmú adminmódosításokat sorosítja; a katalógus-olvasást és más eszközök foglalásait nem. Nem kell hozzá új üzleti tábla. Minden későbbi adminjogot módosító útvonalnak ugyanezt a szabályt kell használnia. Közvetlen SQL nem engedélyezett alkalmazásútvonal.

A saját fiók tényleges módosításakor a kliens is törli a munkamenetet és a szerveradat-gyorsítótárat, majd új belépést kér. A V3 migráció version BIGINT oszlopot ad a meglévő felhasználókhoz,0 kezdőértékkel. A kijelentkezés atomikus tokenvisszavonása a verziót is növeli, így nem írható felül elavult adminűrlappal.

## Fájlok és rétegek

| Fájl / csomag                                                                               | Feladat                                                                |
| ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| backend/.../equipment/EquipmentManagementService.java és EquipmentManagementController.java | Kezelői létrehozás, olvasás, szerkesztés, kivonás és eseményolvasás.   |
| ugyanitt Equipment\*Request.java, EquipmentMapper.java                                      | Validált requestek, megengedett mezők leképezése.                      |
| backend/.../maintenance/MaintenanceService.java, MaintenanceController.java                 | Karbantartási munkafolyamat; a korábbi lezárási útvonal is ide került. |
| ugyanitt MaintenanceMapper.java, \*Request.java, MaintenanceDetailResponse.java             | Külön rövid kölcsönzési és részletes kezelői szerződés.                |
| backend/.../user/AdminUserService.java, AdminUserUpdateRequest.java                         | Verziózott adminparancs, adminzár, tokenvisszavonás.                   |
| backend/src/main/resources/db/migration/V3\_\_user_edit_version.sql                         | Meglévő adatok megtartása mellett felhasználói verzió.                 |
| backend/src/test/java/hu/eszkozpont/ManagementIntegrationTest.java                          | 25 új, valódi JWT-s PostgreSQL API-teszt.                              |
| frontend/src/management/                                                                    | Eszköz-, felhasználó- és karbantartási oldalak, stílusok.              |
| frontend/src/test/Management.test.jsx                                                       | 11 új felületi teszt.                                                  |

A V1 és V2 migrációt nem módosítottuk. A séma továbbra is nyolc üzleti táblából áll. Az adminisztratív név/szerep/tiltás módosítását jelenleg a verzió és a tokenvisszavonás védi; az eszköz/foglalás alapú auditnak nincs felhasználócélt kezelő általános naplófelülete.

## Mit ellenőriznek a tesztek?

- Létrehozás, normalizálás, egyedi kód, FK-k, ismeretlen mezők, elavult verzió, nem létező rekordok és szerepkörök.
- Kivonáskor előzménymegtartás, katalógusból elrejtés, nyitott kölcsönzés/élő igény tiltása és kint lévő eszköz helyvédelme.
- Karbantartási intervallumok, jóváhagyás, kölcsönzés, túlfutás, lemondás és lezárás szabályai.
- Névváltozás, szerepváltás, letiltás/újraengedélyezés, változatlan mentés, tokenek végleges visszavonása.
- Kikényszerített audithiba után nincs félkész eszközmódosítás vagy karbantartás.
- Két API-kérés tényleges zárra várakoztatásával: karbantartás és jóváhagyás; kivonás és igénylés; két admin önlefokozása; ugyanazon felhasználó két módosítása. Csak az egyik egymást kizáró művelet teljesül.
- Külön esetben a várakozó admin jogosultságát a zár elengedése előtt visszavonjuk: az eredmény403, a célfelhasználó nem módosul.

A felületi tesztek a tényleges payloadot, visszajelzést, űrlapmegőrzést, külön megerősítést, saját fiók utáni kijelentkeztetést, dátumkonverziót és megengedett állapotműveleteket vizsgálják. A teljes futás79 backend- és29 frontendteszt.

## Bemutató

1. README szerint indítás, eszközfelelősi belépés. Az Eszközállományból vegyél fel egy külön demóeszközt.
2. Módosíts nevet/leírást, ellenőrizd az eszköz eseményeit. Második nyitott űrlap régi verzióját a szerver visszautasítja.
3. Rögzíts rá jövőbeli karbantartást, majd indokkal mondd le. Második, már elkezdett időszakot javítás után zárj le. A két eset külön műveletet kínál.
4. Nyitott kölcsönzés vagy élő igény nélkül vond ki a demóeszközt. A hallgatói katalógusban már nem, a kezelői listában továbbra is megtalálható.
5. Adminnal nyiss egy felhasználót, tekintsd át a változtatást, majd ments. Saját névváltoztatásnál új belépés szükséges. Demópróba után az eredeti név visszaállítható.
6. Az utolsó admin és a párhuzamosság bizonyítása az automatikus tesztben determinisztikus: nem kell kézzel időzíteni két böngészőt.
7. Ellenőrzés: `./tools/run.ps1 -Task Test`, `./tools/run.ps1 -Task Build`.

## Ellenőrzési határ és következő munka

Az új oldalak a helyi valódi backenddel, mobilon és asztalon ellenőrizve. Kis képernyőmagasságnál az oldalsáv dekorációja elrejtőzik, a navigáció görgethető; a kijelentkezés elérhető marad. A kézi próbához készített demóeszköz kivonva, karbantartásai lezárva/lemondva maradnak. A demóadmin eredeti neve visszaállítva.

Az itt ismertetett egység után a [kategória/helyszín kezelés és az automatikus EXPIRED feldolgozó](torzsadatok-es-lejarat.md) is elkészült. A naptár, időszakra szűrt katalógus és dashboard továbbra is tervezett. A fenti tesztszám az akkori egység állapotát rögzíti; az aktuális összesítés az ellenőrzési jegyzőkönyvben található. Docker/éles telepítés ebben a környezetben nem volt ellenőrizhető; a helyi PostgreSQL-es működést ellenőriztük.

## Technikai források

- [PostgreSQL sor- és advisory zárak](https://www.postgresql.org/docs/current/explicit-locking.html).
- [Tranzakciós advisory zár függvényei](https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS).
- [Spring Data JPA zárolási deklaráció](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
