# Foglaltsági naptár és időszakra szűrt katalógus

Elkészült fejlesztési egység, ellenőrizve 2026. november 8-án. A meglévő foglalási és kölcsönzési folyamatot bővíti olvasási funkciókkal; nincs új üzleti tábla, migráció vagy függőség.

## Felhasználói folyamat

A katalógusban az átvétel és visszahozás dátuma külön űrlapon adható meg, budapesti idő szerint. Gépelés közben még nem változik a találatlista; a **Szabad eszközök keresése** alkalmazza az időszakot. Megmarad a keresőkifejezés, kategória, helyszín és rendezés; az oldalszám nullára áll. Az **Időszak törlése** csak a dátumokat távolítja el. A többi szűrő és a lapozás az alkalmazott időszakot megőrzi.

Az alkalmazott dátumok UTC-s URL-paraméterek. A találat adatlapjára kattintva az igénylőűrlap az adott időpontokkal indul, a felhasználási cél üres. Az adatlapról visszalépő hivatkozás az időszakot is továbbadja a katalógusnak. A kezelői teljes leltárlistán nincs időszakszűrő, az továbbra is nyilvántartásként működik.

Az eszközadatlap alján heti foglaltsági naptár látható: előző/következő hét, aktuális hét, kézi frissítés, jóváhagyott foglalás és karbantartás jelmagyarázata. Alapból az aktuális hét, katalógusból hozott időpontnál annak hete jelenik meg. A napokon átnyúló időszak napokra bontva látszik. Mobilon a napok egymás alá kerülnek. A nap szöveges intervallumai színek nélkül is értelmezhetők.

## Üzleti szabályok

| Helyzet                                                      | Eredmény az időszakra szűrt katalógusban                   |
| ------------------------------------------------------------ | ---------------------------------------------------------- |
| APPROVED foglalás átfed                                      | Az eszköz kimarad.                                         |
| OPEN karbantartás átfed                                      | Az eszköz kimarad.                                         |
| Nyitott kölcsönzés dueAt < most                              | Minden későbbi időszakból kimarad, amíg vissza nem veszik. |
| OPEN karbantartás startsAt <= most és endsAt <= most         | Minden későbbi időszakból kimarad, amíg le nem zárják.     |
| REQUESTED, REJECTED, CANCELLED, EXPIRED vagy COMPLETED igény | Nem zárol időszakot.                                       |
| CLOSED vagy CANCELLED karbantartás                           | Nem zárol időszakot.                                       |
| Pontosan az előző végén kezdődő időszak                      | Az érintkezés önmagában megengedett.                       |
| Kivont eszköz vagy archivált kategória/helyszín              | A katalógusban továbbra sem látszik.                       |

Az átfedés a korábbi [kezdés,vég) szabály: startA < endB és endA > startB. A dátumszűrés ugyanazt az időablakot fogadja, mint az igénylés: jövőbeli, egész perces kezdet, legfeljebb 60 nappal előre; 1 óra–7×24 óra időtartam. A ReservationPeriods validátor mindkét helyen közös. Hibás vagy hiányos dátumpár 400-as hibát ad; a kereső piszkozata megmarad.

Az időszakszűrés NOT EXISTS részlekérdezésekkel az adatbázisban történik, **a lapozás és találatszám előtt**. Nem a már betöltött oldalon távolítjuk el a foglalt eszközöket. A korábbi EquipmentResponse, MapStruct és kapcsolati EntityGraph megmarad.

## Mit jelent a naptár?

Csak az aktuálisan APPROVED foglalások és OPEN karbantartások időszakai szerepelnek benne. Nem teljes történeti használati napló: visszavonás, visszavétel vagy karbantartás-lezárás után az adott akadály eltűnik. A korábbi eseményeket a meglévő, jogosultságvédett történet őrzi.

A lekérhető naptári ablak növekvő és legfeljebb 90×24 óra; a múltbeli napok megengedettek, hogy az aktuális hét is kérhető legyen. Az intervallumokat a szerver a kért ablakra vágja és start/end/kind szerint rendezi. A kliens ezekből készíti a budapesti napi metszeteket. Az éjfélkor záruló szakasz 24:00, a következő napon nem jelenik meg. Az óraátállítási hét 167 vagy 169 valós órája helyesen képeződik UTC-re.

A két globális akadály, overdueLoan és overdueMaintenance külön jelző. Akkor is blokkolhatja a későbbi igénylést, amikor a kiválasztott héten nincs átfedő intervallum. A felület ilyenkor figyelmeztetést és **Átmenetileg zárolt** szöveget ad. Egyébként az üres jövőbeli nap felirata **Nincs jelzett akadály**: a keresés és naptár nem tartja fenn az eszközt.

A lekérdezések pillanatképet adnak. Másik felhasználó közben kapott jóváhagyása miatt a következő igénylés továbbra is 409-cel visszatérhet. Az igénylés/jóváhagyás/átadás változatlanul az eszközsorzár után ellenőriz. A naptár és a szűrt katalógus 30 másodpercenként frissül; a kapcsolódó helyi parancsok a query cache-t is érvénytelenítik.

## Adatvédelem és szerződések

`GET /api/equipment/{id}/availability?from=...&to=...` hitelesítést igényel. USER/MANAGER/ADMIN ugyanazt az anonimizált választ kapja. Nem létező, kivont vagy archivált törzsadatú eszközre 404 érkezik. Másik felhasználó foglalási adatlapja ettől továbbra is védett.

A BlockingInterval kizárólag startsAt, endsAt és kind mezőt ad. Nincs név, e-mail, cél, indok vagy foglalásazonosító. A repository konstruktorprojekció eleve csak az intervallummezőket olvassa. A válasz további mezői: equipmentId, from, to, checkedAt és a két késési jelző. Új írási DTO nincs; pontos szerződések: [API](api-terv.md), [DTO-k](dto-terv.md), [ADR-013](architektura.md).

## Érintett fájlok

| Hely a projekt gyökeréhez képest                                                                                     | Feladat                                                              |
| -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| backend/src/main/java/hu/eszkozpont/equipment/availability/                                                          | Anonimizált DTO-k, olvasási service és controller, adatbázisszűrés.  |
| backend/src/main/java/hu/eszkozpont/equipment/EquipmentController.java és EquipmentService.java                      | Opcionális időszakparaméterek és összetett keresés.                  |
| backend/src/main/java/hu/eszkozpont/reservation/ReservationPeriods.java                                              | Közös igénylési/keresési időablak-validáció.                         |
| backend/src/main/java/hu/eszkozpont/reservation/ReservationRepository.java és maintenance/MaintenanceRepository.java | Minimális intervallumprojekciók és túlfutási jelző.                  |
| frontend/src/availability/                                                                                           | Keresési időszakűrlap, heti naptár, dátumsegédek, saját CSS.         |
| frontend/src/pages/CatalogPage.jsx és EquipmentPage.jsx                                                              | Dátumszűrés és adatlapi integráció.                                  |
| frontend/src/reservations/ReservationForm.jsx                                                                        | Előtöltött időszak és frissítés.                                     |
| Meglévő foglalási/kölcsönzési/kezelési oldalak                                                                       | A foglaltságot érintő írás után az új lekérdezések érvénytelenítése. |
| backend/src/test/java/hu/eszkozpont/AvailabilityIntegrationTest.java                                                 | 18 PostgreSQL-es integrációs eset.                                   |
| frontend/src/test/Availability.test.jsx                                                                              | 14 felületi és naptári időkezelési eset.                             |

## Ellenőrzés és bemutató

```powershell
.\tools\run.ps1 -Task Test
.\tools\run.ps1 -Task Build
```

A teljes tesztcsomag **122 backendteszt és 53 frontendteszt**, mind sikeres, kihagyás nélkül. Külön célzott backendfuttatás: a backend könyvtárában `./mvnw -Dtest=AvailabilityIntegrationTest test`.

A backendtesztek ellenőrzik az anonimizálást, hitelesítést/szerepköröket, aktív katalógus védelmét, időhatárokat, típusokat és állapotokat, metszeteket, késedelmes akadályokat, helyes lapozást és a keresés utáni új ütközés visszautasítását. A frontendtesztek a tényleges API-paramétereket, űrlapmegőrzést, URL-átvitelt, naptárnavigációt, hibakezelést, napokra bontást és mindkét óraátállítást vizsgálják.

Kézi bemutató:

1. Indítsd a README szerint, lépj be hallgatóként. Keress egy eszközt jövőbeli, 1–3 órás időszakra; nyisd meg és ellenőrizd az előtöltött igénylést.
2. Adj fel külön, éjfélen átnyúló demóigényt. Másik kezelő hagyja jóvá. A naptár következő hetén két napra bontva látszik, a felhasználó neve/célja nélkül.
3. Ugyanerre az időszakra a keresésből kimarad; a végéhez pontosan csatlakozó időszak megengedett. Dátumok nélkül újra a teljes aktív katalógus látszik.
4. Mobilon a napok egymás alatti kártyák. Visszavonás után naptárfrissítéssel az akadály eltűnik, az eseménytörténet megmarad.

Ez a folyamat a valódi helyi backenddel, asztalon és 390×844-es nézetben is ellenőrizve. A külön demófoglalás a próba végén visszavonva maradt; sem meglévő rekordot, sem történetet nem töröltünk. A naptár jóváhagyott állapotáról mentett képernyőkép a helyi, figyelmen kívül hagyott `.local` könyvtárban található.

Következő tervezett egység: a szerepkörhöz igazított dashboard valódi összesítésekkel és teendőkkel. Docker/éles közzététel ebben az egységben sem történt.
