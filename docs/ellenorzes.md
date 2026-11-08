# Ellenőrzési jegyzőkönyv – EszközPont

Frissítve: 2026. november 8. A tesztek helyben ténylegesen lefutottak; nem pusztán tervezett ellenőrzések. Az alábbi böngészős lista a korábbi és jelenlegi egységek ellenőrzéseit is rögzíti.

## Automatikus ellenőrzések

- Backend: **122 teszt, 0 hiba, 0 kihagyás**. Ebből 12 alapfunkció, 20 foglalási, 22 kölcsönzési, 25 kezelési, 23 törzsadat-/lejárati integrációs eset, 18 naptár-/időszakszűrési eset, 1 ütemező-integrációs eset és 1 job-unit teszt. Az integrációs tesztek valódi PostgreSQL18.3-on, Flyway V1–V4 migrációval és Hibernate-sémavalidálással futnak; Java26.0.2.1 futtató / release25 cél.
- Frontend: **53 React/Vitest teszt** sikeres. A korábbi 39 mellett 14 új eset készült a dátumszűréshez és naptárhoz. Ellenőrzik az alkalmazás előtti űrlapállapotot, UTC/URL-paramétereket, lapozás visszaállítását, megőrzött többi szűrőt, hiba utáni adatmegőrzést, adatlapra és igénylésbe átvitelt, hétnavigációt, globális akadályjelzést, újrapróbálást és az óraátállítás két irányát. Az összes korábbi teszt is lefutott.
- Backend csomagolás: sikeres futtatható Spring Boot jar.
- Frontend build: sikeres Vite production build.

A backendtesztek többek között aláírt, de lejárt, hibás célközönségű, hibás felhasználóazonosítójú és hiányzó audience mezőjű tokeneket is visszautasítanak. A párhuzamos regisztrációból egy201 és egy409 születik.

A foglalási tesztek ellenőrzik a létrehozást, lekérést, tulajdonosi adatvédelmet, szerepköröket, verziót, állapotátmeneteket, lemondást és időhatárokat. Vizsgálják az inaktív eszközt/törzsadatot/felhasználót, karbantartást és késedelmes kölcsönzést is. Mesterséges audit-korlátsértés után a foglalás állapota is visszagörgetődik.

Három párhuzamossági teszt két valós JWT-s API-kérést indít, és a pg_stat_activity alapján megvárja mindkettő adatbázis-zárra várakozását. Átfedő foglalásoknál egy200 és egy409; azonos foglalásnál egy200 és egy409, dupla esemény nélkül; szomszédos időszakoknál két200 az eredmény. A tesztek saját ideiglenes adatbázisban futnak.

A kölcsönzési csomag további három párhuzamossági esete: két átadás, két sérült visszavétel, illetve átadás és kezelői visszavonás. Egyetlen átadás/visszavétel marad, nincs dupla karbantartás és nincs visszavont foglaláshoz tartozó kölcsönzés. A közvetlen SQL-lel megkísérelt második nyitott kölcsönzést az adatbázis is visszautasítja. Audit-korlátsértéskor az átadás, illetve a sérült visszavétel összes része visszagörgetődik.

Az injektált tesztórát a kezdési/határidő-határokra léptetjük. Pontosan a határidő nem késés; utána1 nanoszekundum már igen. Visszavétel után a késési percek nem nőnek. Az időléptetéshez csak a kölcsönzési teszt24 órás token-élettartamot használ; a termék30 perces beállítása változatlan.

A kezelési csomag ellenőrzi az eszközverziót, a kód/FK-korlátokat, a kint lévő eszköz helyvédelmét, a kivonást, a kézi karbantartási ütközést és a tokenvisszavonást. A kezelési időléptetéses tesztek szintén kizárólag tesztben használnak24 órás token-élettartamot. A négy kétkéréses zárteszt: karbantartás/jóváhagyás, kivonás/igénylés, két admin önlefokozása, egy felhasználó két szerkesztése. Külön egykéréses zárteszt igazolja, hogy a várakozás közben lefokozott admin sem módosíthat. Az eszköz- és karbantartási audit hibája az adatváltozást is visszagörgeti.

A törzsadatcsomag a két teljes életciklust, normalizálást, egyediséget (közvetlen SQL-próbával is), védett mezőket, keresést/szűrést/lapozást, RBAC-ot, nyilvános és admin DTO különbségét, verzióütközést és a kapcsolt eszközök védelmét vizsgálja. Tényleges zárvárakozással fut az archiválás/igénylés, archiválás/eszközfelvitel és két párhuzamos szerkesztés próbája. A függő igény és a lejárt, de nyitott kölcsönzés is megakadályozza az archiválást.

A lejárati tesztek ellenőrzik a pontos endsAt határt és az azt 1 nanoszekundummal megelőző időt, mindkét kiinduló állapotot, a kölcsönzések kizárását, a közben visszavont jelöltet, a legfeljebb 100-as rendezett adagot és az audit-visszagörgetést. Két lejárati parancs, illetve lejárat és visszavonás versenyében egyetlen végállapot és egyetlen lezáró esemény marad. Egy unit teszt igazolja a hibás rekord utáni folytatást.

A külön ReservationExpirySchedulerIntegrationTest valóban elindítja a Spring-ütemezőt saját PostgreSQL-adatbázissal. Rövid, csak tesztbeli időközzel, közvetlen service-parancs nélkül lezár egy múltbeli igényt, ismételt futások után is egy eseménnyel. Valódi bejelentkezéssel kapott JWT-vel az API-ban is ellenőrzi az EXPIRED állapotot és a null actorú eseményt. A többi integrációs tesztben az ütemező kikapcsolt, hogy ne zavarja az órával vezérelt teszteseteket.

A naptár-/szűrési csomag bizonyítja a 401-es védelmet, a másik felhasználó adatainak anonimizálását és a három szerepkör azonos foglaltsági válaszát. Vizsgálja az intervallumok metszetét, típusát, rendezését, a pontos csatlakozást, hibás vagy hiányos dátumokat, 90 napos olvasási korlátot, a keresés 1 óra–7 nap/60 nap határait, offseteket és inaktív eszközök elrejtését. A lapozási tesztben az első két eszköz blokkolt: a szabad találatok száma és második oldala mégis helyes. Külön esetben keresés után új jóváhagyás készül: az ezt követő igénylés 409, nincs elvesző ütközésellenőrzés. A PostgreSQL-es késéshatárt pontosan a határidőnél és a tárolási pontosságnak megfelelően 1 mikroszekundummal utána ellenőrizzük.

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
- Meglévő helyi adatokra a V2 migráció sikeresen felment; nincs adatbázistörlés.
- A jelenlegi idő után pár perccel induló foglalás a felületen létrehozva, másik kezelővel jóváhagyva. Kezdés előtt tiltott átadás; időablakon belül tényleges átadás, szerveridő és esemény.
- A nyitott kölcsönzési listából sérült visszavétel: COMPLETED foglalás, sérülésleírás, a megadott tervezett végű OPEN karbantartás és események.
- Mobilon javítás lezárása, megjegyzés és esemény; lezárt kölcsönzési lista.390px adatlap/lista vízszintes oldaltúlcsordulás nélkül. A széles táblázat saját konténerben görgethető.
- V3 migráció meglévő helyi adatokra sikeres; a korábbi demófiókok és eszközök megmaradtak.
- Eszközfelelősként új demóeszköz felvétele, nevének szerkesztése, tervezett karbantartás felvitele/indokolt lemondása, elkezdett javítás lezárása és eszköz kivonása. Minden esemény az eszköztörténetben látszik; kivont eszköz kezelői keresésből megnyitható.
- Az eszközszerkesztő és karbantartási adatlap390px szélességen túlcsordulás nélkül.720px magas asztali nézetben a kijelentkezés elérhető; a bővített menühöz igazított görgethető oldalsáv/dekorációjavítás ellenőrizve.
- Admin saját megjelenített nevének módosítása áttekintéssel: mentés után új belépés szükséges. Újrabelépés után az eredeti név visszaállítva, majd ismét új belépés. A szerepkör és tiltás változatlan maradt a kézi próbában; ezek működését az API-tesztek bizonyítják.
- V4 migráció sikeresen lefutott a meglévő helyi adatokon. Adminnal kategória létrehozása és szerkesztése, archiválása, visszaaktiválása, majd ismét archiválása a tényleges felületen.
- Tárolási hely felvétele, kódjának nagybetűs normalizálása, helyiségének szerkesztése, archiválás megerősítéssel és Archivált szűrés. 390×844-es mobilnézetben az oldal nem csordul túl; a táblázat saját keretben görgethető. Az ellenőrzéshez felvett külön kategória és helyszín archiváltan megmarad, az eredeti demótörzsadatok változatlanok.
- Időszakra szűrt katalógus a valódi backenddel; az URL UTC-időpontokat tartalmaz. Az eszközadatlap igénylőűrlapja ugyanazt az időszakot mutatja budapesti időben.
- Külön, 23:00–02:00 demóigény benyújtása, másik kezelővel jóváhagyása. A heti naptárban 23:00–24:00, majd 00:00–02:00 jelenik meg, név és cél nélkül. A katalógusban az átfedő időszakra nulla, a pontosan 02:00-kor csatlakozó egyórás időszakra egy Canon-találat.
- A naptár390×844-es nézetben egymás alatti napkártyákkal, oldalszintű vízszintes túlcsordulás nélkül. A böngésző ideiglenes mérete visszaállítva. A demóigény visszavonása után kézi naptárfrissítéssel a két akadály eltűnt; az igény és eseményei megmaradtak. A vizsgált hallgatói lapon nem keletkezett böngészőkonzol-hiba.

A foglalási böngészőpróba helyi demóadatai megmaradnak; a tesztkörnyezet nem törli őket. A kipróbált időpontok elmúltával a bemutatóhoz új, jövőbeli időszakot kell megadni.

Az első böngészős próba megmutatta a localhost / 127.0.0.1 CORS-eltérést; javítva a local profil két pontos originjével. Az éles konfiguráció továbbra is a külön megadott originre korlátoz.

## Ellenőrzési határok

Docker/Compose ebben a környezetben nincs kipróbálva. Nincs éles telepítés vagy külső hálózati teszt. A dashboard még tervezett. A késés, a párhuzamos adminvédelem és a tényleges ütemezett lejárat határait automatizált teszt bizonyítja; a kézi böngészőpróba nem várta ki a többórás visszahozási határidőt, nem módosított adminjogot és nem várt ki külön lejáratot. A végleges törlést saját, ideiglenes tesztadatbázisban ellenőriztük, a meglévő helyi demóadatokat nem töröltük. A naptár a jelenlegi akadályokat mutatja, történeti használati statisztikát nem helyettesít.
