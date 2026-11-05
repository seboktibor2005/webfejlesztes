# Ellenőrzési jegyzőkönyv – 1. fejlesztési egység

2026. november 5. A tesztek helyben ténylegesen lefutottak; nem pusztán tervezett ellenőrzések.

## Automatikus ellenőrzések

- Backend: 12 integrációs teszt, 0 hiba, 0 kihagyás. Valódi PostgreSQL 18.3, Flyway-migráció és Hibernate-sémavalidálás, Java 26.0.2.1 futtató / release 25 cél.
- Frontend: 3 React/Vitest teszt sikeres. Védett útvonal, magyar belépési hiba, sikeres belépés és USER szerepnél elrejtett adminmenü.
- Backend csomagolás: sikeres futtatható Spring Boot jar.
- Frontend build: sikeres Vite production build.

A backendtesztek többek között aláírt, de lejárt, hibás célközönségű, hibás felhasználóazonosítójú és hiányzó audience mezőjű tokeneket is visszautasítanak. A párhuzamos regisztrációból egy 201 és egy 409 születik. Ez még nem foglalási párhuzamossági teszt: az a következő egység feladata.

## Böngészős próba

- Hallgatói bejelentkezés tényleges backendválasszal.
- Nyolc aktív eszköz az adatbázisból.
- „canon” keresésre egy aktív találat és működő adatlap.
- Mobilkatalógus, kinyitható menü, kijelentkezés és sikeres visszavonási visszajelzés.
- Adminisztrátori belépés és négy demófelhasználó listázása.
- Mobilnézetben nincs vízszintes túlcsordulás; a zárt menü elemei nem kerülnek a hozzáférhetőségi fába.
- Helyi backend újraindítása meglévő PostgreSQL-adatokkal: séma és demóadatok megmaradnak.

Az első böngészős próba megmutatta a localhost / 127.0.0.1 CORS-eltérést; javítva a local profil két pontos originjével. Az éles konfiguráció továbbra is a külön megadott originre korlátoz.

## Ellenőrzési határok

Docker/Compose ebben a környezetben nincs kipróbálva. Nincs éles telepítés vagy külső hálózati teszt. A foglalás, elbírálás, kölcsönzés, karbantartás és dashboard még tervezett funkció. A kiadott első egység ezek kész működését nem állítja.
