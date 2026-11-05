# Átadás, visszavétel és késések

## Működő folyamat és szabályok

A MANAGER/ADMIN a jóváhagyott foglalás adatlapján adhat át eszközt. A szerver ellenőrzi a beküldött foglalási verziót, az APPROVED állapotot, a korábbi átadás hiányát, az aktív eszközt/kategóriát/helyszínt és az aktív igénylőt. Az időablak startsAt <= most < endsAt. Átadáskor a tényleges időpont és a kezelő a szervertől származik, a határidő a foglalás vége. Az állapot GOOD vagy WORN; a foglalás az átadás után APPROVED marad, nyitott kölcsönzéssel.

Az eszköz nem adható át, ha bárkinél még kint van, akkor sem, ha pontosan most jár le az előző határidő. A hátralévő foglalási idővel ütköző OPEN karbantartás is akadály. A már elkezdett OPEN karbantartás a tervezett vége után is zárol a kezelői lezárásig.

Visszavételkor a nyitott kölcsönzés tényleges visszavételi időt, kezelőt, állapotot és opcionális megjegyzést kap. A foglalás COMPLETED lesz. Közben letiltott igénylő vagy kivont eszköz visszavétele továbbra is lehetséges. Átvett foglalás már nem vonható vissza: a tényleges visszahozást kell rögzíteni. Az ismételt átadás/visszavétel409.

## Késés és figyelmeztetések

Jelenlegi késés: returnedAt == null és dueAt < most. Pontosan a határidő még nem késés. A perceket felfelé kerekítjük, ezért egy másodperc késés1 megkezdett perc. Visszavétel után overdue=false, de returnedLate megőrzi a késve érkezés tényét, és overdueMinutes a tényleges visszavételnél rögzült időközből számítódik. Nincs adatbázisban tárolt, elavuló OVERDUE állapot.

A kezelő Kölcsönzések listája nyitott, késésben lévő, lezárt és minden kölcsönzésre szűrhető, lapozott, dueAt/id szerint rendezett. A szerver azonos kérésen belül ugyanazzal a Clock-pillanattal számol. A lista és a foglalási adatlap30 másodpercenként frissül, amikor az oldal aktív. A visszavétel nem történik meg automatikusan.

A késedelmesen kint lévő eszköz vagy OPEN karbantartás miatt veszélyeztetett későbbi APPROVED foglalás atRisk figyelmeztetést kap. A foglalás megmarad. A hallgató saját adatlapján is látja a figyelmeztetést, az akadályt okozó másik felhasználó személyes adatait nem. Az átadás pillanatában a backend ismét ellenőriz mindent.

## Sérülés és karbantartási zár

DAMAGED visszavételnél legalább5 karakteres, legfeljebb1000 karakteres leírás és jövőbeli maintenanceEndsAt kötelező. A visszavétel azonnal OPEN karbantartást hoz létre, a kölcsönzéshez kapcsolva. A teljes sérülésleírás a kölcsönzésen és az auditeseményben marad; a karbantartás rövid, rögzített okot kap. Ez biztonsági zár, ezért meglévő későbbi jóváhagyás mellett is létrejöhet.

A kezelő ugyanazon az adatlapon, a javítás ellenőrzése után, külön megerősítéssel zárja le a karbantartást. A tervezett vége önmagában nem oldja fel a zárat. A lezárási megjegyzés az eseménytörténetben megmarad. Az általános karbantartási lista és kézi felvitel azóta a [kezelési egységben](kezelesi-fejlesztesi-egyseg.md) elkészült.

## Tranzakció és párhuzamosság

Minden írás ugyanazt az equipment_item sort zárolja, mint a foglalási modul. Először skalár eszközazonosító, utána sorzár, csak azután teljes foglalás/kölcsönzés/karbantartás olvasás. Így nincs zár előtt betöltött régi JPA-állapot. Egy eszközhöz egyszerre legfeljebb egy nyitott kölcsönzést a PostgreSQL részleges UNIQUE indexe is biztosít. A foglalásazonosító a loan táblában szintén egyedi.

Átadás és esemény; visszavétel, COMPLETED állapot, szükséges karbantartás és események; illetve karbantartási lezárás és esemény: közös tranzakciók. Auditírás hibájánál egyik rész sem marad félkészen. A V1 migráció változatlan. A V2 új, opcionális-egyedi maintenance_period.loan_id kapcsolatot és a nyitott kölcsönzések határidőindexét adja hozzá, meglévő adatok törlése nélkül.

## Fájlok

- backend/src/main/java/hu/eszkozpont/loan/: entitás, állapottípusok, DTO-k, MapStruct-leképezés, repository, service és kezelői controller.
- backend/src/main/java/hu/eszkozpont/maintenance/: a sérülési zár entitása, repositoryja, olvasási és lezárási DTO-ja. A visszavétellel együttes létrehozást a LoanService, a lezárást és általános kezelést a MaintenanceService végzi.
- backend/src/main/java/hu/eszkozpont/reservation/: kölcsönzési adatokkal és veszélyeztetettséggel bővített válaszok. Listánként csoportos kölcsönzés- és kockázatlekérdezés, nem soronkénti service-hívások.
- backend/src/main/resources/db/migration/V2\_\_loan_safety_maintenance.sql: bővítő migráció.
- backend/src/test/java/hu/eszkozpont/LoanIntegrationTest.java:22 új, valódi PostgreSQL-es API-teszt.
- frontend/src/loans/LoanPanel.jsx és loans.css: magyar átadási/visszavételi/javítási műveletek.
- frontend/src/pages/LoansPage.jsx: kezelői munkalista; foglalási adatlapra vezető hivatkozások.
- frontend/src/test/Loans.test.jsx:7 új felületi teszt. A foglalási dátumbevitel input-eseményét a meglévő igénylési teszt is ellenőrzi.

## Kézi bemutató

1. Indítás a README szerint. Hallgatóként kérj a jelenlegi időnél néhány perccel későbbi, legalább1 órás foglalást.
2. Másik kezelővel hagyd jóvá még a kezdete előtt. Az átadási gomb a kezdésig tiltott.
3. A kezdet után nyisd meg az adatlapot, válassz átadási állapotot, majd erősítsd meg. Átadott jelvény, tényleges időpont és esemény jelenik meg; nincs többé visszavonás.
4. A Kölcsönzések nyitott listájában nyisd meg az eszközt. Vedd vissza jó vagy használt állapotban: a foglalás lezárul, és a lezárt listába kerül.
5. Másik átadás után válassz sérült visszavételt. Indok és jövőbeli karbantartásvég nélkül nem rögzíthető.
6. Érvényes sérülési adatokkal zár keletkezik. A későbbi jóváhagyás figyelmeztetést kap, átadás blokkolt. Javítás után külön zárd le a karbantartást; az esemény megjelenik.
7. A késéshez nem kell órákat várni: az automatikus teszt pontosan határidőre, utána1 nanoszekundumra, késve visszavételre és későbbi lekérésre lépteti a befecskendezett órát.
8. Teljes ellenőrzés: `./tools/run.ps1 -Task Test`, majd `./tools/run.ps1 -Task Build`.

## Mit bizonyítanak a tesztek?

A22 új backendteszt valódi belépéssel szerzett JWT-t használ. Az óra előreléptetését tesztre korlátozott24 órás JWT-élettartam teszi lehetővé; a termék30 perces élettartama változatlan. Nincs kihagyott hitelesítés vagy mockolt szerepkör. Két API-kérés adatbázis-zárra várakozását a pg_stat_activity alapján ellenőrizzük, majd egyszerre engedjük őket tovább.

- Két átadás: egy201, egy409; egy kölcsönzés és egy átadási esemény.
- Két sérült visszavétel: egy200, egy409; egy karbantartás és egy lezárás.
- Átadás és kezelői lemondás: csak az egyik teljesülhet; lemondott foglaláshoz nem marad kölcsönzés.
- Kikényszerített auditírási hiba: nincs félbehagyott átadás, visszavétel vagy karbantartás.
- Szerepkör, idegen adatok, állapotok, kötelező mezők, határidők, letiltott fiók, karbantartás és részleges egyedi index szintén ellenőrizve.

A felületi tesztek megerősítést, futó művelet tiltását, jogosultságfüggő gombokat, sérülési feltételeket, budapesti időkonverziót, hiba utáni megmaradó adatokat és listaszűrést vizsgálnak. Az óraátállítás korábbi tesztjei megmaradnak.

## Határok

Nincs e-mail, bírság, automatikus visszavétel vagy határidő-hosszabbítás. Az átvétel nélkül elmúlt foglalások EXPIRED feldolgozója továbbra is későbbi feladat. A zárkezelés alkalmazásútvonalakra érvényes; közvetlen SQL nem kerülheti meg az üzleti szabályokat.
