# Törzsadatkezelés és automatikus foglaláslejárat

Ez az egység a meglévő eszköz-, foglalás-, kölcsönzés- és karbantartás-kezelésre épül. Nem vezet be új üzleti táblát vagy új függőséget. A termék és a visszajelzések nyelve továbbra is magyar.

## Elkészült működés

Az ADMIN a **Törzsadatok** menüben kezeli a kategóriákat és tárolási helyeket. Mindkét listában keresés, aktív/archivált szűrés és lapozás működik. Új rekord aktív; meglévő rekord adatai szerkeszthetők, archiválhatók és visszaaktiválhatók. A két utóbbi művelethez, valamint a törléshez külön áttekintés és megerősítés kell. USER és MANAGER a backend adminvégpontjait sem érheti el.

A kategórianév trimelt, kisbetűfüggetlenül egyedi. A helyszínkód trimelt, nagybetűs és egyedi; betűt, számot, kötőjelet és aláhúzást tartalmazhat, első karaktere betű vagy szám. Az egyediséget PostgreSQL-index is védi, az archivált rekordok neve/kódja is foglalt marad.

Minden módosító kérés a szerkesztés kezdetekor látott verziót küldi. Másik admin változtatása után 409-es hiba jön; a piszkozat megmarad. A friss adatok betöltése külön, tudatos művelet, amely felülírja a piszkozatot. Az archiválás nem teszi csendben aktuálissá a korábban megnyitott űrlapot.

## Kapcsolatok és archiválás

- Bármilyen kapcsolt eszköz mellett a törlés tiltott, a kivont eszköz is számít. Így a korábbi foglalások és kölcsönzések kapcsolatai megmaradnak.
- Archiválás tiltott, ha bármelyik kapcsolt eszköznek nyitott kölcsönzése vagy még élő REQUESTED/APPROVED igénye van. Élő: endsAt > most; a függő igény is ide tartozik.
- A kint lévő eszköz kölcsönzése a határidő után is nyitott, ezért továbbra is védi az archiválást.
- Archivált kategória/helyszín eszközei eltűnnek a hallgatói katalógusból, új igény/jóváhagyás/átadás nem engedett. A kezelői nyilvántartásban és a történetben megmaradnak.
- Visszaaktiválás után az egyébként aktív eszközök ismét láthatók. A már archivált kapcsolatot az eszközszerkesztő is feliratozza; módosításhoz aktív kapcsolatot kell választani vagy adminnal visszaaktiválni.

Az archiválás egyszerre több eszközt érinthet. A service előbb kizáró törzsadatzárat, majd növekvő azonosító szerint eszközsorzárakat kér. Az eszközfelvitel/-módosítás megosztott törzsadatzárat kér; a foglalási és kölcsönzési parancsok ugyanazokat az eszközsorokat zárolják. Emiatt az ellenőrzés és archiválás közé párhuzamos új eszköz vagy foglalás sem kerülhet. Részletes indoklás: az architektúradokumentum ADR-011 döntése.

## Átvétel nélkül lejárt igények

A Spring-ütemező alapból percenként dolgozik, az indulás után először egy percet vár. Ez **a foglalási időszak végét** figyeli: a kezdés elmúlása még nem jelenti automatikusan az igény végét.

EXPIRED állapotra csak akkor vált, ha mindhárom feltétel igaz:

1. A foglalás REQUESTED vagy APPROVED.
2. Az endsAt legfeljebb a jelen pillanat.
3. Semmilyen kölcsönzés nem tartozik hozzá.

A feldolgozás az eszközsorzár után frissen újraellenőriz, és egy tranzakcióban menti a foglalást meg a RESERVATION_EXPIRED eseményt. A végrehajtó null, a felületen **Rendszer**, az esemény felirata **Átvétel nélkül lejárt**. A történet a foglalás meglévő, jogosultságvédett adatlapján olvasható. Nincs külön HTTP-parancs, amellyel a felhasználó lejáratot állíthatna.

Két párhuzamos feldolgozás vagy egy közben végrehajtott visszavonás sem készít második lezáró eseményt. Auditmentési hibánál az állapotváltozás is visszagördül. Egy hibás rekord után a többi feldolgozása folytatódik; a következő futás újra kiválaszthatja a hibásat. Egy adag legfeljebb 100, időszakvég és id szerint rendezett jelölt. Nagyobb hátralék több futásban dolgozódik fel; ez nem másodpercre pontos határidőjelző szolgáltatás.

Az átadott eszköz **nem válik automatikusan visszavetté**. A nyitott kölcsönzés késését a meglévő számítás jelzi, és továbbra is tényleges kezelői visszavétel kell.

Beállítások környezeti változóként: `RESERVATION_EXPIRY_ENABLED` (alapérték true), `RESERVATION_EXPIRY_DELAY` (alapérték PT1M). A késleltetés a kezdeti várakozásra és az előző futás vége utáni várakozásra is vonatkozik. A gyökér `.env.example` csak minta; az indítási környezet adja át a változókat.

## Fájlok és felelősségek

| Hely a projekt gyökeréhez képest                                                     | Feladat                                                                         |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| backend/src/main/resources/db/migration/V4\_\_reference_versions_and_unique_keys.sql | Kategória/helyszín verziója és normalizált egyedi indexek. V1–V3 változatlan.   |
| backend/src/main/java/hu/eszkozpont/equipment/reference/                             | Request/response recordok, MapStruct mapper, admincontroller és üzleti service. |
| backend/src/main/java/hu/eszkozpont/equipment/ReferenceDataLock.java                 | Megosztott és kizáró tranzakciós törzsadatzár.                                  |
| backend/src/main/java/hu/eszkozpont/equipment/EquipmentManagementService.java        | Eszközírások együttműködése a törzsadatzárral.                                  |
| backend/src/main/java/hu/eszkozpont/reservation/ReservationExpiryService.java        | Jelöltlista és egyetlen lejárat tranzakciója.                                   |
| backend/src/main/java/hu/eszkozpont/reservation/ReservationExpiryJob.java            | Ütemezés és rekordonkénti hibakezelés.                                          |
| backend/src/main/java/hu/eszkozpont/reservation/ReservationRepository.java           | Rendezett, korlátos lejárati jelöltlekérdezés.                                  |
| frontend/src/management/ReferenceDataPage.jsx                                        | Közös, explicit kategória/helyszín változatú lista és szerkesztő.               |
| frontend/src/management/reference.css                                                | A meglévő arculathoz illeszkedő, reszponzív elrendezés.                         |
| frontend/src/App.jsx és components/AppLayout.jsx                                     | Védett adminútvonalak és navigáció.                                             |
| frontend/src/pages/ReservationPage.jsx és management/EquipmentEditorPage.jsx         | Magyar lejárati eseményfelirat; utóbbiban archivált kapcsolatok jelzése is.     |

Pontos végpontok: [API-terv](api-terv.md). Mezők és validációk: [DTO-szerződések](dto-terv.md). A törzsadatváltozásokat jelenleg verzió védi; nem készült hozzájuk külön adminaudit. Az audit továbbra is eszköz/foglalás alapú.

## Automatikus ellenőrzés

```powershell
.\tools\run.ps1 -Task Test
.\tools\run.ps1 -Task Build
```

Az egység 25 backendteszttel és 10 frontendteszttel bővült. A teljes ellenőrzés eredménye: **104 backendteszt és 39 frontendteszt sikeres**, kihagyás nélkül. A backendcsomagolás és a frontend production build is sikeres.

- `ReferenceAndExpiryIntegrationTest`: 23 saját PostgreSQL-es eset; törzsadat-életciklus, egyediség, DTO/RBAC, hivatkozás, verzió, párhuzamos archiválás/igénylés/eszközfelvitel/szerkesztés. Lejárati időhatárok, kölcsönzés kizárása, 100-as adag, visszagörgetés és versenyhelyzetek.
- `ReservationExpiryJobTest`: egy rekord hibája után a következőt is feldolgozza.
- `ReservationExpirySchedulerIntegrationTest`: valódi Spring-ütemező és külön PostgreSQL, rövid tesztidőköz; a háttérfolyamat magától zárja le a múltbeli igényt, majd valódi JWT-s API-olvasás ellenőrzi az állapotot és rendszereseményt.
- `ReferenceData.test.jsx`: 10 felületi eset; beküldött mezők/verzió, hiányzó opcionális leírás kezelése, külön megerősítések, sikeres és tiltott törlés, piszkozatmegőrzés és explicit újratöltés, szűrés, lejárati esemény.

A többi backendtesztben az ütemező ki van kapcsolva, hogy az injektált tesztóra léptetésével ne versenyezzen. A tesztadatbázisok különállók, a tartós helyi demóadatokat nem törlik.

## Kézi bemutató

1. Indítsd a README szerint a backendet és frontendet, majd lépj be adminnal.
2. A Törzsadatok menüben vegyél fel egy külön kategóriát; módosítsd a leírását. Nyiss két szerkesztést, ments az egyikben, majd a régi verzióval a másikban: a piszkozat megmarad, újratöltés kérhető.
3. Hivatkozás nélküli kategóriát archiválj, szűrj Archivált állapotra, majd aktiváld vissza. Az adminműveletekhez külön megerősítés jelenik meg.
4. Vegyél fel tárolási helyet kisbetűs kóddal, ellenőrizd a nagybetűs eredményt. Mobilnézetben módosíts helyiséget; a lista a saját keretében görgethető.
5. Hivatkozott kategória/helyszín törlése hibával tér vissza. Élő foglalási igény mellett az archiválás is tiltott; ezt az elkülönített API-tesztek determinisztikusan bemutatják.
6. A lejárat gyors bizonyításához futtasd a külön ütemező-integrációs tesztet a backend könyvtárában: `./mvnw -Dtest=ReservationExpirySchedulerIntegrationTest test`. JDK 25+ szükséges. Nem kell az adatbázisban demóidőpontokat átírni vagy a többórás időszakot kivárni.

A böngészős ellenőrzés során külön demókategória és -helyszín készült, szerkesztéssel, archiválással és visszaaktiválással; a végén archiváltan maradtak. 390×844-es mobilnézet is ellenőrizve, oldalszintű vízszintes túlcsordulás nélkül. Végleges törlést a kézi próba nem végzett; azt a saját tesztadatbázis bizonyítja. A V4 a meglévő helyi adatokra törlés nélkül felment.

## Következő egység

A naptár, az időszakra szűrt katalógus és a szerepkörhöz igazított, valódi adatokból épülő dashboard még tervezett. Ezekhez az itt kialakított archiválási és lejárati szabályokat is figyelembe kell venni. Új szolgáltatás vagy telepítés ehhez az egységhez nem szükséges.
