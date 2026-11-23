# EszközPont – védési forgatókönyv

Tervezett idő **8–10 perc**. A bemutató egy egyetemi kamera útját követi az igényléstől a javításig, három szerepkörön keresztül.

## Előkészítés

- A projekt gyökeréből indítsd a backendet és a frontendet két külön PowerShell-terminálban. A pontos előfeltételek a [README-ben](../README.md#helyi-indítás-windows-alatt-docker-nélkül) vannak.

  ```powershell
  .\tools\run.ps1 -Task Backend
  # A másik terminálban
  .\tools\run.ps1 -Task Frontend
  ```

- A védés előtt nem sokkal nyisd meg a [helyi felületet](http://localhost:5173/belepes) három külön fülön. Lépj be Annával, az eszközfelelőssel és az adminisztrátorral a [demófiókok](../README.md#helyi-indítás-windows-alatt-docker-nélkül) adataival. Egy negyedik fülön Bencével mutathatod meg az elkülönített hallgatói hozzáférést. A belépés 30 percig érvényes, a teljes oldal újratöltése után újra be kell lépned.
- Készíts két aktív eszközt egy külön demókategóriában, például **Védési kamera A** és **Védési kamera B** néven. Legyen egyedi a leltári számuk, és jegyezd fel a kereséshez. Újabb próbához adj új nevet vagy leltári számot.
- **A kamera** lesz a helyszíni igényléshez és ütközéshez. Válassz egy szabad, legalább egyórás jövőbeli időszakot. Ha a jóváhagyási ütközést is megmutatod, Bencével még a bemutató előtt küldj be egy átfedő igényt, és hagyd elbírálásra váró állapotban. Két függő igény még elfér ugyanabban az ablakban.
- **B kamerához** Annával előre küldj be egy igényt, majd kezelőként hagyd jóvá. Az időablak a védés várható kezdete előtt 5–10 perccel induljon, a tervezett befejezés után is tartson, és legyen legalább egyórás. Az igénylést és a jóváhagyást az időablak kezdete előtt végezd el. Ha csúszhat a védés, adj hosszabb ablakot, és kezdetkor ellenőrizd, hogy az **Eszköz átadása** gomb elérhető. Minden bevitt időpont budapesti idő szerinti.
- Jegyezd fel a B kamera foglalásának azonosítóját. A kezelői áttekintésen ellenőrizd az átadási statisztika kiinduló értékét. Készíts elő képernyőképet egy már végigpróbált, lezárt kölcsönzés eseménytörténetéről arra az esetre, ha az élő átadási időablak elcsúszik.

## Élő bemutató

| Idő | Szerepkör és művelet | Mit érdemes megmutatni? |
| --- | --- | --- |
| 0:00–0:45 | **Hallgató.** Nyisd meg Anna áttekintését és a katalógust. | „A hallgató megkeresi az eszközt és látja a saját teendőit. Az eszközfelelős a tényleges átadást és visszavételt rögzíti.” |
| 0:45–2:15 | **Hallgató.** Keresd meg az A kamerát, adj meg időszakot a katalógusban, majd nyisd meg az adatlapot és küldd be az igényt. | A katalógusból átkerül az időszak az űrlapra. Az igény a saját listában **Elbírálásra vár** állapotban jelenik meg. Jegyezd meg az azonosítót. |
| 2:15–3:45 | **Eszközfelelős.** Az áttekintésről nyisd meg az elbírálási listát. Hagyd jóvá Anna A kamerás igényét. Ha előkészítetted Bence átfedő igényét, próbáld azt is jóváhagyni. | Anna eseménytörténetében látható a döntés és az elbíráló. A második jóváhagyás ütközést jelez. Az igény függőben marad. |
| 3:45–4:30 | **Hallgató.** Frissítsd az A kamera naptárát, majd keress a jóváhagyott időszakra. | A naptár foglaltságot mutat. Az átfedő időszakra az A kamera kiesik a találatok közül. A foglaltsági adatokban nincs név vagy igénylési cél. |
| 4:30–5:45 | **Eszközfelelős.** Nyisd meg az előre jóváhagyott B kamerás foglalást és rögzítsd a jó állapotú átadást. **Hallgatóként** frissítsd az áttekintést. | Az átadás megjelenik az eseménytörténetben. Anna látja, hogy az eszköz nála van, és mikor kell visszahoznia. A kezelői kategóriastatisztikában eggyel nő az átadások száma. |
| 5:45–6:30 | **Adminisztrátor.** Mutasd meg a felhasználók listáját, majd a **Törzsadatok** oldalon próbáld archiválni a demókategóriát. | Az adminfelület külön menüpont. Nyitott kölcsönzéshez vagy élő foglaláshoz kapcsolódó kategória archiválását a szerver visszautasítja. |
| 6:30–8:00 | **Eszközfelelős.** A B kamerát vedd vissza **Sérült** állapotban. Írd be a sérülést és a tervezett karbantartásvég jövőbeli időpontját. Mutasd meg a hallgatói naptárban a karbantartást, majd kezelőként zárd le. | Egy visszavétellel lezárul a foglalás és létrejön a karbantartási zárolás. Lezárás után frissítsd a naptárat. A zárolás eltűnik, a visszavétel és a javítás eseménye megmarad. |
| 8:00–9:30 | **Technikai rész.** Nyisd meg az ER-diagramot, majd az API- vagy teszttervet. | Mutasd végig az eszköz → foglalás → kölcsönzés → karbantartás kapcsolatot. Válassz ki egy párhuzamos jóváhagyási vagy jogosultsági tesztet, és mondd el, melyik hibát fogja meg. |

A külön fülön belépett Bencének opcionálisan add meg Anna foglalási adatlapjának URL-jét. A bejelentkezett Bence sem fér hozzá a foglaláshoz vagy az eseményeihez. Késéshez használj előre előkészített, már lejárt visszahozási határidejű, még nyitott kölcsönzést. Enélkül elég a határesetet lefedő tesztet megmutatni.

Ha a saját fiókról kérdeznek, mutasd meg a **Fiókom** oldalt és a jelszócsere integrációs tesztjét. A teszt igazolja, hogy csere után a korábbi JWT és a régi jelszó is elutasításra kerül. A közös demóbelépést a védésen érdemes megőrizni.

## Rövid válaszok a technikai kérdésekre

- **Mi történik, ha két kezelő egyszerre hagy jóvá?** A service az érintett eszköz adatbázissorát zárolja, majd a záron belül újraellenőrzi az átfedést. Az intervallum vége nyitott, így két közvetlenül egymás után kezdődő foglalás elfér. A jóváhagyás és az esemény együtt kerül mentésre.
- **Mi védi a másik hallgató adatait?** A Spring Security ellenőrzi a JWT-t. Az aktuális fiók és tokenverzió is ellenőrzött. A szerver a JWT alanyából veszi a felhasználói azonosítót, majd a foglaláson tulajdonost vagy kezelői jogot ellenőriz. A naptár csak a foglaltsági időszakokat adja ki. A 401 a hiányzó vagy érvénytelen hitelesítés, a 403 a szerepkör tiltása, más hallgató foglalására pedig 404 a válasz.
- **Mire használod a MapStructot?** Például az eszközválaszban az entitás kapcsolt kategóriáját és tárolási helyét DTO-vá képezi. Az írási DTO és a service szabja meg a módosítható mezőket. A felhasználói válaszban nincs jelszómező.
- **Miért PostgreSQL?** A tartós demóadat, a Flyway-migráció és a párhuzamos műveletek ellenőrzése ugyanazzal az adatbázismotorral működik fejlesztéskor és az integrációs teszteken. A sorzár és az adminműveletek tranzakciós zára is az adatbázisra támaszkodik.
- **Mit mér az áttekintés?** A hallgató a saját aktuális teendőit látja. A kezelői grafikon a ténylegesen átadott eszközöket számolja az utolsó 30 budapesti naptári napra. Egy visszavett kölcsönzés átadása is beleszámít.
- **Mi történik, ha valaki nem jön az eszközért?** Az át nem vett, lejárt igényt a háttérfolyamat lezárja és naplózza. Az átadott eszköz késésbe kerül, és az eszközfelelős visszavételi művelete zárja le.

A technikai részhez nyisd meg ezeket a dokumentumokat. [Adatmodell és tranzakciók](architektura.md), [API-terv](api-terv.md), [ellenőrzési jegyzőkönyv](ellenorzes.md).

## Próba után

Ha újra futtatnád a jelenetet, új B kamerás igényt készíts. A korábban lezárt kölcsönzés visszanézhető az előzményekben. A demóeszközöket az élő igények és kölcsönzések rendezése után kezelőként vond ki, majd adminisztrátorként archiváld a külön kategóriát. A tesztek külön PostgreSQL-adatbázison futnak, így a védéshez előkészített adatok megmaradnak.
