# REST API-terv és aktuális állapot

Alapcím helyben: http://localhost:9090/api. Minden útvonal hitelesített, kivéve POST auth/login és auth/register. A local profil Swagger-címe /swagger-ui/index.html; ez kizárólag az implementált végpontokat mutatja.

## Elkészült végpontok

| Metódus és út                              | Kérés                                                      | Válasz                                                   | Jogosultság                              |
| ------------------------------------------ | ---------------------------------------------------------- | -------------------------------------------------------- | ---------------------------------------- |
| POST /auth/register                        | RegisterRequest                                            | 201 UserResponse; Location:/api/me                       | Nyilvános                                |
| POST /auth/login                           | LoginRequest                                               | 200 LoginResponse                                        | Nyilvános                                |
| POST /auth/logout                          | Üres                                                       | 204; minden korábbi token visszavonva                    | Belépett                                 |
| GET /me                                    | –                                                          | UserResponse                                             | Belépett                                 |
| GET /categories                            | –                                                          | CategoryResponse[]                                       | Belépett                                 |
| GET /locations                             | –                                                          | LocationResponse[]                                       | Belépett                                 |
| GET /equipment                             | q?, categoryId?, locationId?, page=0,size=12,sort=name,asc | PageResponse<EquipmentResponse>                          | Belépett; csak aktív katalógus           |
| GET /equipment/{id}                        | –                                                          | EquipmentResponse                                        | Belépett; inaktív/nem létező:404         |
| GET /management/equipment                  | Katalógusparaméterek                                       | PageResponse<EquipmentResponse>                          | MANAGER/ADMIN; kivontakat is tartalmaz   |
| GET /admin/users                           | page=0,size=20                                             | PageResponse<AdminUserResponse>                          | ADMIN                                    |
| POST /reservations                         | ReservationCreateRequest                                   | 201 ReservationResponse; Location:/api/reservations/{id} | Belépett                                 |
| GET /me/reservations                       | status?,page=0,size=12,sort=createdAt,desc                 | PageResponse<ReservationSummaryResponse>                 | Saját                                    |
| GET /reservations/{id}                     | –                                                          | ReservationResponse                                      | Saját vagy MANAGER/ADMIN; idegen404      |
| GET /reservations/{id}/events              | page=0,size=20                                             | PageResponse<AuditEventResponse>                         | Foglaláséval azonos                      |
| POST /reservations/{id}/cancel             | CancelRequest                                              | ReservationResponse                                      | Saját, kezdés és átadás előtt            |
| GET /management/reservations               | status?,page=0,size=12,sort=createdAt,desc                 | PageResponse<ReservationSummaryResponse>                 | MANAGER/ADMIN                            |
| POST /management/reservations/{id}/approve | ApproveRequest                                             | ReservationResponse                                      | MANAGER/ADMIN; saját jóváhagyása tiltott |
| POST /management/reservations/{id}/reject  | RejectRequest                                              | ReservationResponse                                      | MANAGER/ADMIN                            |
| POST /management/reservations/{id}/cancel  | CancelRequest; kötelező indok                              | ReservationResponse                                      | MANAGER/ADMIN; átadás előtt              |

Méret:1–100. Negatív oldalszám, hibás FK-szűrő, túl hosszú keresés:400. Rendezési fehérlista:name,asc; name,desc; inventoryCode,asc. Név/leltári szám keresés kisbetűfüggetlen; % és \_ literálisként kezelt. Üres találat:200 üres contenttel. A lista másodlagos rendezése id. A szerver nem fogad el tetszőleges property pathot rendezésként.

Az eszköz lifecycle állapota nem időszakos elérhetőség. A dátumszűrt katalógus és az elérhetőségi naptár későbbi egység. A foglalási parancsok már valódi ütközésvizsgálatot végeznek.

A foglalási listák támogatott rendezése: createdAt,desc; startsAt,asc; startsAt,desc, stabil másodlagos id-rendezéssel. Méret1–100, nulla alapú oldal. Az események időrendben, azonos időnél id szerint következnek. A kezelői lista ebben az egységben csak státuszszűrőt fogad; igénylő-, eszköz- és dátumszűrő még nincs. Idegen foglalás és annak eseményei USER számára404. Minden foglalási állapotváltó parancs az utolsó válaszból kapott versiont várja.

## Kölcsönzési végpontok – elkészültek

| Metódus és út                               | Kérés / paraméterek           | Siker                                                     | Jogosultság   |
| ------------------------------------------- | ----------------------------- | --------------------------------------------------------- | ------------- |
| POST /management/reservations/{id}/checkout | CheckoutRequest               | 201 LoanResponse, Location:/api/management/loans/{loanId} | MANAGER/ADMIN |
| GET /management/loans                       | open?,overdue?,page=0,size=12 | PageResponse<LoanResponse>                                | MANAGER/ADMIN |
| GET /management/loans/{id}                  | –                             | LoanResponse                                              | MANAGER/ADMIN |
| POST /management/loans/{id}/return          | ReturnRequest                 | LoanResponse                                              | MANAGER/ADMIN |
| POST /management/maintenance/{id}/close     | MaintenanceCloseRequest       | MaintenanceResponse                                       | MANAGER/ADMIN |

Kölcsönzési rendezés fix: dueAt,asc majd id,asc; méret1–100. Az open=false és overdue=true együtt400 INVALID_FILTER. Az overdue=false az éppen nem késésben lévő rekordokat jelenti, beleértve a lezártakat. A késve visszahozás külön returnedLate mező. USER saját kölcsönzési adatai a védett foglalási válasz loan mezőjében olvashatók, általános kezelői kölcsönzési hozzáférés nélkül.

Átadáskor startsAt <= most < endsAt, aktív eszköz/törzsadat/igénylő, jóváhagyás, friss version, és nyitott kölcsönzés/karbantartási akadály hiánya szükséges. A határidő nem küldhető be. Sérült visszavétel azonnali karbantartást nyit; az endsAt elérése nem helyettesíti a lezárást. Részletes szabályok: [kölcsönzési egység](harmadik-fejlesztesi-egyseg.md).

## Kezelési végpontok – elkészültek

| Metódus és út                            | Kérés / paraméterek                 | Válasz                                                       | Jogosultság                                 |
| ---------------------------------------- | ----------------------------------- | ------------------------------------------------------------ | ------------------------------------------- |
| GET /management/equipment/{id}           | –                                   | EquipmentResponse, kivont eszközre is                        | MANAGER/ADMIN                               |
| GET /management/equipment/{id}/events    | page=0,size=20                      | PageResponse<AuditEventResponse>, időrend/id                 | MANAGER/ADMIN                               |
| POST /management/equipment               | EquipmentCreateRequest              | 201 EquipmentResponse, Location az új kezelői adatlapra      | MANAGER/ADMIN                               |
| PUT /management/equipment/{id}           | EquipmentUpdateRequest              | EquipmentResponse                                            | MANAGER/ADMIN                               |
| POST /management/equipment/{id}/retire   | EquipmentRetireRequest              | EquipmentResponse                                            | MANAGER/ADMIN                               |
| GET /management/maintenance              | equipmentId?,status?,page=0,size=12 | PageResponse<MaintenanceDetailResponse>, startsAt/id szerint | MANAGER/ADMIN                               |
| POST /management/maintenance             | MaintenanceCreateRequest            | 201 MaintenanceDetailResponse, Location az új rekordra       | MANAGER/ADMIN                               |
| GET /management/maintenance/{id}         | –                                   | MaintenanceDetailResponse                                    | MANAGER/ADMIN                               |
| POST /management/maintenance/{id}/cancel | MaintenanceCancelRequest            | MaintenanceDetailResponse                                    | MANAGER/ADMIN, csak még el nem kezdett OPEN |
| GET /admin/users/{id}                    | –                                   | AdminUserResponse                                            | ADMIN                                       |
| PATCH /admin/users/{id}                  | AdminUserUpdateRequest              | AdminUserResponse                                            | ADMIN                                       |

A karbantartás lezárásának már működő végpontja ugyanazon az útvonalon maradt, a MaintenanceService kezeli. Az eszközszerkesztés és kivonás version mezőt kér; elavult adat409 STALE_VERSION. Kivonás nyitott kölcsönzés vagy REQUESTED/APPROVED, endsAt > most igény mellett409 EQUIPMENT_IN_USE. Kint lévő eszköz helyváltoztatása409 LOCATION_LOCKED. A leltári szám létrehozás után nem módosítható.

A kézi karbantartás jövőbeli végű, egész perces időszakot vár; már elkezdett javítás is rögzíthető. Még élő jóváhagyással, kint lévő eszközzel vagy blokkoló OPEN karbantartással ütközés409 MAINTENANCE_CONFLICT. A sérült visszavétel automatikus biztonsági zára továbbra is kivétel. Lemondáshoz5–500 karakteres indok kell; lezárás csak elkezdett időszakra, opcionális megjegyzéssel.

A felhasználó módosítása displayName/role/enabled/version mezőket kér. Minden tényleges változás visszavonja az érintett korábbi tokenjeit; változatlan mentés nem. Az utolsó aktív admin letiltása/lefokozása409 LAST_ADMIN. A jogosultságot a közös tranzakciós adminzár megszerzése után is ellenőrizzük. Saját módosítás után új belépés szükséges. E-mail/jelszó nem módosítható ezen az útvonalon.

## Törzsadatkezelés – elkészült

Minden alábbi útvonal kizárólag ADMIN számára érhető el. A service a módosítások közös zárának megszerzése után az aktív adminjogot is ellenőrzi. A hitelesített, aktív választékot adó `GET /categories` és `GET /locations` szerződése változatlan; az adminválasz külön `active` és `version` mezőt tartalmaz.

| Metódus és út                       | Kérés / paraméterek              | Siker                                                    |
| ----------------------------------- | -------------------------------- | -------------------------------------------------------- |
| GET /admin/categories               | q?,active?,page=0,size=20        | PageResponse<CategoryAdminResponse>                      |
| GET /admin/categories/{id}          | –                                | CategoryAdminResponse                                    |
| POST /admin/categories              | CategoryCreateRequest            | 201 CategoryAdminResponse; Location az új adminadatlapra |
| PUT /admin/categories/{id}          | CategoryUpdateRequest            | CategoryAdminResponse                                    |
| PATCH /admin/categories/{id}/active | ActiveRequest                    | 204                                                      |
| DELETE /admin/categories/{id}       | Kötelező version query paraméter | 204                                                      |
| GET /admin/locations                | q?,active?,page=0,size=20        | PageResponse<LocationAdminResponse>                      |
| GET /admin/locations/{id}           | –                                | LocationAdminResponse                                    |
| POST /admin/locations               | LocationCreateRequest            | 201 LocationAdminResponse; Location az új adminadatlapra |
| PUT /admin/locations/{id}           | LocationUpdateRequest            | LocationAdminResponse                                    |
| PATCH /admin/locations/{id}/active  | ActiveRequest                    | 204                                                      |
| DELETE /admin/locations/{id}        | Kötelező version query paraméter | 204                                                      |

A keresés kisbetűfüggetlen: kategóriánál név, tárolási helynél név/kód. A `%`, `_` és `\` literális karakter. A keresés maximum 120 karakter, az oldal nulla alapú, a méret 1–100. Az `active` elhagyva minden állapotot, true/false csak az adott állapotot adja. Rendezés kategóriánál name/id, helynél code/id; szabad sort paraméter nincs.

Új rekord aktív és verziója 0. PUT csak a módosítható mezőket és a kötelező verziót fogadja. PATCH például `{"active":false,"version":0}`; törlés például `DELETE /api/admin/categories/7?version=0`. A 204 válasz után a friss verzió az adminolvasásból kérhető le. Változatlan mentés/aktiválás nem növel verziót; elavult kérés ekkor is 409 STALE_VERSION.

Kategórianév és helyszínkód normalizált egyedisége adatbázisindexszel is védett: 409 CATEGORY_NAME_TAKEN / LOCATION_CODE_TAKEN. Bármilyen kapcsolt eszköz, a kivont is, megakadályozza a törlést: 409 REFERENCE_IN_USE. Archiváláskor az összes kapcsolt eszköz élő REQUESTED/APPROVED igényét (endsAt > most) és nyitott kölcsönzését vizsgáljuk; bármelyik 409 REFERENCE_BUSY. Archivált törzsadat eszközei a hallgatói katalógusból eltűnnek, kezelői listából megmaradnak. Visszaaktiválás visszaállítja az egyébként aktív eszközök láthatóságát. Ismeretlen kategória/helyszín 404 CATEGORY_NOT_FOUND / LOCATION_NOT_FOUND.

## Automatikus lejárat – elkészült, belső háttérfolyamat

Ehhez nincs HTTP-parancs. Alapból engedélyezett, egyperces kezdeti várakozással és a futások befejezése között egyperces késleltetéssel. Egy futás legfeljebb 100, endsAt/id szerint rendezett jelöltet vesz át. Az egyes parancsok külön tranzakcióban, a közös eszközsorzár után újraellenőrzik: REQUESTED vagy APPROVED, endsAt <= most, semmilyen Loan nem tartozik hozzá. Ezután EXPIRED állapot és RESERVATION_EXPIRED esemény készül atomikusan. Az actor null, a felület „Rendszer” feliratot mutat. A már átadott eszköz késés marad, automatikus visszavétel nincs.

Egy rekord hibája visszagörgeti saját változását és naplózódik; a többi jelölt feldolgozása folytatódik. Ismételt vagy párhuzamos feldolgozás nem dupláz eseményt. Környezeti beállítások: RESERVATION_EXPIRY_ENABLED, RESERVATION_EXPIRY_DELAY. A foglalási válasz és esemény API-formája változatlan; az EXPIRED állapot ténylegesen előállhat.

## Tervezett végpontok – még nem használhatók

| Metódus és út                    | Kérés / paraméterek                      | Siker                           | Jogosultság                               |
| -------------------------------- | ---------------------------------------- | ------------------------------- | ----------------------------------------- |
| PUT /me                          | ProfileUpdateRequest                     | UserResponse                    | Saját                                     |
| POST /me/password                | PasswordChangeRequest                    | 204                             | Saját, minden JWT visszavonva             |
| GET /equipment/{id}/availability | from,to; max90nap                        | AvailabilityResponse            | Belépett; anonimizált                     |
| GET /equipment                   | startsAt?,endsAt? együtt; eredeti szűrők | PageResponse<EquipmentResponse> | Csak a kért időszakra foglalható eszközök |
| GET /dashboard/me                | –                                        | UserDashboardResponse           | Saját                                     |
| GET /management/dashboard        | –                                        | ManagementDashboardResponse     | MANAGER/ADMIN                             |

Későbbi listák rendezése is explicit fehérlistás lesz. Státuszparancsok nem PATCH status-ként működnek. Az endpoint elérése nem helyettesíti a service-beli tulajdonos- és állapotellenőrzést. PUT teljes mezőkészletet vár a módosítható mezőkből; a szerverkezelt mezők tiltottak.

## Példák

Regisztráció: {"displayName":"Minta Anna","email":"anna@example.test","password":"SajatJelszo2026!"}.

Belépés: {"email":"anna@example.test","password":"SajatJelszo2026!"}. További kérés fejléc: Authorization: Bearer <accessToken>.

Katalógus: GET /api/equipment?q=canon&categoryId=1&page=0&size=12&sort=name,asc.

Igénylés: {"equipmentId":1,"startsAt":"2026-11-12T09:00:00+01:00","endsAt":"2026-11-12T12:00:00+01:00","purpose":"Egyetemi interjúprojekt felvétele"}. A konkrét dátum mindig jövőbeli legyen.

Jóváhagyás: POST /api/management/reservations/1/approve, törzs: {"version":0}. Elutasítás: /reject, törzs: {"version":0,"reason":"Az eszközt erre a feladatra nem tudjuk biztosítani."}. A version mindig az adott rekord aktuális értéke.

A REQUESTED igények átfedhetnek. APPROVED foglalással ütköző igény létrehozása vagy jóváhagyása409 RESERVATION_CONFLICT. A [kezdés,befejezés) intervallumok miatt a pontosan csatlakozó időszakok megengedettek. Elavult verzió409 STALE_VERSION, tiltott állapotváltás409 INVALID_TRANSITION, saját jóváhagyás403 SELF_APPROVAL. Inaktív eszköz, törzsadat, blokkoló karbantartás vagy késedelmes kölcsönzés409 EQUIPMENT_UNAVAILABLE. A döntés és az auditbejegyzés közös tranzakcióban készül.

## Ellenőrzés

A backend integrációs tesztjei valódi POST loginból kapott JWT-t használnak; nem csupán mockolt szerepköröket. A teljes backendcsomag 104 tesztje közül 25 a törzsadatkezelést, lejárati szabályokat és ütemezőt ellenőrzi. A párhuzamos kérések tényleges adatbázis-zárra várakozását is bizonyítjuk. Részletek: [foglalás](masodik-fejlesztesi-egyseg.md), [kölcsönzés](harmadik-fejlesztesi-egyseg.md), [kezelés](kezelesi-fejlesztesi-egyseg.md), [törzsadatok és lejárat](torzsadatok-es-lejarat.md).
