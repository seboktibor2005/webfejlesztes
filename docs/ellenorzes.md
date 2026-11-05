# Ellenőrzési jegyzőkönyv – 1–2. fejlesztési egység

2026. november 5. A tesztek helyben ténylegesen lefutottak; nem pusztán tervezett ellenőrzések.

## Automatikus ellenőrzések

- Backend: 32 integrációs teszt, 0 hiba, 0 kihagyás. Ebből12 alapfunkció és20 foglalási teszt. Valódi PostgreSQL 18.3, Flyway-migráció és Hibernate-sémavalidálás, Java 26.0.2.1 futtató / release 25 cél.
- Frontend: 11 React/Vitest teszt sikeres. Belépés, útvonalvédelem, jogosultságfüggő menü, igénylési payload, hiba esetén megőrzött űrlapadatok, saját jóváhagyás tiltása, döntési megerősítés, futó kérés tiltása és jóváhagyási ütközés. Külön tesztek a budapesti téli/nyári időre, az óraátállítás hiányzó/kétértelmű idejére és hibás dátumokra.
- Backend csomagolás: sikeres futtatható Spring Boot jar.
- Frontend build: sikeres Vite production build.

A backendtesztek többek között aláírt, de lejárt, hibás célközönségű, hibás felhasználóazonosítójú és hiányzó audience mezőjű tokeneket is visszautasítanak. A párhuzamos regisztrációból egy201 és egy409 születik.

A foglalási tesztek ellenőrzik a létrehozást, lekérést, tulajdonosi adatvédelmet, szerepköröket, verziót, állapotátmeneteket, lemondást és időhatárokat. Vizsgálják az inaktív eszközt/törzsadatot/felhasználót, karbantartást és késedelmes kölcsönzést is. Mesterséges audit-korlátsértés után a foglalás állapota is visszagörgetődik.

Három párhuzamossági teszt két valós JWT-s API-kérést indít, és a pg_stat_activity alapján megvárja mindkettő adatbázis-zárra várakozását. Átfedő foglalásoknál egy200 és egy409; azonos foglalásnál egy200 és egy409, dupla esemény nélkül; szomszédos időszakoknál két200 az eredmény. A tesztek saját ideiglenes adatbázisban futnak.

## Böngészős próba

- Hallgatói bejelentkezés tényleges backendválasszal.
- Nyolc aktív eszköz az adatbázisból.
- „canon” keresésre egy aktív találat és működő adatlap.
- Mobilkatalógus, kinyitható menü, kijelentkezés és sikeres visszavonási visszajelzés.
- Adminisztrátori belépés és négy demófelhasználó listázása.
- Mobilnézetben nincs vízszintes túlcsordulás; a zárt menü elemei nem kerülnek a hozzáférhetőségi fába.
- Helyi backend újraindítása meglévő PostgreSQL-adatokkal: séma és demóadatok megmaradnak.
- Hallgatóként két azonos időszakú, függő igény beküldése az eszközadatlapról, saját foglalási adatlap és létrehozási esemény.
- Eszközfelelősként az első jóváhagyása: jóváhagyott állapot, elbíráló és esemény jelenik meg.
- A második jóváhagyásakor magyar ütközési hiba; az igény függő marad, jóváhagyási esemény nem keletkezik.
- 390px mobilnézetben a foglalási adatlap és a kezelői lista ellenőrizve. A lista táblázata saját görgetőkonténerben marad; az oldal szélessége nem nő meg. A kézi próba során talált, rejtett táblafejlécből eredő túlcsordulás javítva.

A foglalási böngészőpróba helyi demóadatai megmaradnak; a tesztkörnyezet nem törli őket. A kipróbált időpontok elmúltával a bemutatóhoz új, jövőbeli időszakot kell megadni.

Az első böngészős próba megmutatta a localhost / 127.0.0.1 CORS-eltérést; javítva a local profil két pontos originjével. Az éles konfiguráció továbbra is a külön megadott originre korlátoz.

## Ellenőrzési határok

Docker/Compose ebben a környezetben nincs kipróbálva. Nincs éles telepítés vagy külső hálózati teszt. A kölcsönzés átadás/visszavétel, a karbantartási szerkesztés, a naptár, a dátumszűrt katalógus, az automatikus lejárat és a dashboard még tervezett funkció. A nyitott kölcsönzés és karbantartás blokkolását SQL-tesztadatokkal ellenőriztük; ezekhez még nincs felviteli végpont.
