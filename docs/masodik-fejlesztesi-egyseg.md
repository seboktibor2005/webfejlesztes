# Foglalás és jóváhagyás – 2. fejlesztési egység

## A működő folyamat

Az eszközadatlapról benyújtható igény, majd a Saját foglalások listából megnyitható az adatlapja és eseménytörténete. Az Igények elbírálása menüpont MANAGER/ADMIN számára látszik, és a backend is ellenőrzi a jogosultságot. A kezelő jóváhagyhat, indokkal elutasíthat vagy lemondhat igényt. Saját igényét nem hagyhatja jóvá. A hallgató saját, még el nem kezdett és át nem adott foglalását vonhatja vissza.

A REQUESTED igények átfedhetnek. APPROVED időszakkal új igénylés sem ütközhet. [kezdés, befejezés) intervallumokkal dolgozunk: egy 12:00-kor befejeződő foglalás után indulhat másik 12:00-kor.

## A párhuzamos jóváhagyás védelme

Két kérés egyszerre láthatná szabadnak ugyanazt az időszakot. Ezért minden érintett írás először az eszköz sorát zárolja. A foglalás teljes entitását csak a zár megszerzése után olvassuk; előtte csak az eszköz skalár azonosítóját kérjük le. Így az első szintű JPA-cache sem tarthat meg régi foglalási állapotot. PostgreSQL READ COMMITTED mellett a várakozás utáni új lekérdezés már látja az előző tranzakció végleges döntését.

A beküldött version az elavult képernyőből érkező módosítást akadályozza meg. Másik igény átfedése RESERVATION_CONFLICT, régi verzió STALE_VERSION, tiltott állapotátmenet INVALID_TRANSITION: mindegyik 409. A döntés és az esemény egy tranzakció, így a naplóírás hibája a döntést is visszagörgeti.

## Fájlok és felelősségek

| Fájl vagy csomag                                                       | Feladata                                                                    |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| backend/src/main/java/hu/eszkozpont/reservation/Reservation.java       | A meglévő séma JPA-entitása, @Version mezővel.                              |
| ugyanitt ReservationRepository.java                                    | Lapozás, előtöltés, átfedés, meglévő karbantartás és kölcsönzés vizsgálata. |
| ugyanitt ReservationService.java                                       | Validáció, jogosultságok, állapotváltás, sorzárolás, tranzakció.            |
| ugyanitt *Request.java, *Response.java, ReservationMapper.java         | Külön request/response szerződések és valódi MapStruct-leképezés.           |
| ugyanitt \*Controller.java                                             | HTTP-adapterek és kezelői metódusvédelem.                                   |
| backend/src/main/java/hu/eszkozpont/audit/                             | Hozzáfűzéssel használt eseményentitás és olvasási DTO.                      |
| backend/src/main/java/hu/eszkozpont/equipment/EquipmentRepository.java | Az eszközsor zárolása.                                                      |
| backend/src/main/java/hu/eszkozpont/common/ApiExceptionHandler.java    | Párhuzamos módosítások egységes 409 válasza.                                |
| backend/src/test/java/hu/eszkozpont/ReservationIntegrationTest.java    | 20 valódi PostgreSQL-es API-teszt.                                          |
| frontend/src/reservations/                                             | Igénylőűrlap, állapotfeliratok, budapesti időkezelés, modulstílusok.        |
| frontend/src/pages/ReservationsPage.jsx, ReservationPage.jsx           | Listák, adatlap, döntési megerősítés, eseménytörténet.                      |
| frontend/src/test/Reservations.test.jsx, reservation-time.test.js      | Igénylés, döntés, hibajelzés és óraátállítás tesztjei.                      |

A V1 migráció változatlan: nincs adatbázistörlés és nincs szükség sémamódosításra. A natív repository-lekérdezések a már létrehozott kölcsönzési/karbantartási táblák adatait is ellenőrzik; ezek szerkesztőfelülete később készül el.

## Kézi bemutató

1. A README szerint indítsd az alkalmazást. Anna demófiókjával nyiss egy eszközt.
2. Adj meg jövőbeli, 1 óra–7 nap közötti időszakot és legalább 10 karakteres célt. Küldd be az igényt: REQUESTED állapot és létrehozási esemény jelenik meg.
3. Küldj be még egy igényt ugyanarra az eszközre, átfedő idővel. Ez még megengedett.
4. Jelentkezz ki, majd eszközfelelősként lépj be. Az Igények elbírálása listából nyisd meg az elsőt, és erősítsd meg a jóváhagyást.
5. A második jóváhagyása magyar ütközési hibát ad. Függő marad, jóváhagyási esemény nem keletkezik.
6. Tulajdonosként vond vissza az első, még el nem kezdett foglalást. A második ezután jóváhagyható.
7. Próbálj indok nélkül elutasítani: az űrlap és a backend is elutasítja. Érvényes indok megjelenik az adatlapon és a történetben.
8. Másik hallgató közvetlen foglalás- vagy esemény-URL-re 404-et, USER kezelői API-ra 403-at kap.
9. Ellenőrzés: `./tools/run.ps1 -Task Test`, majd `./tools/run.ps1 -Task Build`.

## Mit bizonyítanak a tesztek?

A párhuzamossági teszt külön adatbázis-kapcsolaton ideiglenesen zárolja az eszközsort, és két valódi JWT-s API-kérést indít. A pg_stat_activity nézetben megvárja, hogy mindkét kérés ténylegesen adatbázis-zárra várjon. Csak ezután engedi el a zárat.

- Két átfedő igény: egy 200 és egy 409; egy APPROVED rekord és egy jóváhagyási esemény.
- Ugyanaz az igény kétszer: egy 200 és egy 409; nincs dupla esemény.
- Két szomszédos időszak: két 200.

További esetek: időhatárok, tiltott mezők, inaktív eszköz/törzsadat/felhasználó, karbantartás és késés, tiltott állapotváltások, lapozás, idegen rekordok és mesterséges adatbázis-korláttal kiváltott audit-visszagörgetés. A frontend a beküldött adatokat, döntési megerősítést, futó művelet tiltását, hibajelzést és óraátállítást ellenőrzi.

## Határok

Az átadás/visszavétel, karbantartási szerkesztés, naptár, dátumszűrt katalógus és automatikus EXPIRED feldolgozó későbbi egység. A böngésző időzónájától függetlenül Europe/Budapest szerint alakítjuk át az időpontokat; a kettős őszi óra és a hiányzó tavaszi időpont helyett másik időpontot kell választani.
