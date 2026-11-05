# REST API-terv és aktuális állapot

Alapcím helyben: http://localhost:9090/api. Minden útvonal hitelesített, kivéve POST auth/login és auth/register. A local profil Swagger-címe /swagger-ui/index.html; ez kizárólag az implementált végpontokat mutatja.

## Elkészült végpontok

| Metódus és út             | Kérés                                                      | Válasz                                | Jogosultság                            |
| ------------------------- | ---------------------------------------------------------- | ------------------------------------- | -------------------------------------- |
| POST /auth/register       | RegisterRequest                                            | 201 UserResponse; Location:/api/me    | Nyilvános                              |
| POST /auth/login          | LoginRequest                                               | 200 LoginResponse                     | Nyilvános                              |
| POST /auth/logout         | Üres                                                       | 204; minden korábbi token visszavonva | Belépett                               |
| GET /me                   | –                                                          | UserResponse                          | Belépett                               |
| GET /categories           | –                                                          | CategoryResponse[]                    | Belépett                               |
| GET /locations            | –                                                          | LocationResponse[]                    | Belépett                               |
| GET /equipment            | q?, categoryId?, locationId?, page=0,size=12,sort=name,asc | PageResponse<EquipmentResponse>       | Belépett; csak aktív katalógus         |
| GET /equipment/{id}       | –                                                          | EquipmentResponse                     | Belépett; inaktív/nem létező:404       |
| GET /management/equipment | Katalógusparaméterek                                       | PageResponse<EquipmentResponse>       | MANAGER/ADMIN; kivontakat is tartalmaz |
| GET /admin/users          | page=0,size=20                                             | PageResponse<AdminUserResponse>       | ADMIN                                  |

Méret:1–100. Negatív oldalszám, hibás FK-szűrő, túl hosszú keresés:400. Rendezési fehérlista:name,asc; name,desc; inventoryCode,asc. Név/leltári szám keresés kisbetűfüggetlen; % és \_ literálisként kezelt. Üres találat:200 üres contenttel. A lista másodlagos rendezése id. A szerver nem fogad el tetszőleges property pathot rendezésként.

Az eszköz lifecycle állapota nem időszakos elérhetőség. A dátumszűrés csak a foglalási modul elkészültekor kerül be; az első egység nem közöl kitalált szabad/foglalt adatot.

## Tervezett végpontok – még nem használhatók

| Metódus és út                               | Kérés / paraméterek                                        | Siker                                              | Jogosultság                               |
| ------------------------------------------- | ---------------------------------------------------------- | -------------------------------------------------- | ----------------------------------------- |
| PUT /me                                     | ProfileUpdateRequest                                       | UserResponse                                       | Saját                                     |
| POST /me/password                           | PasswordChangeRequest                                      | 204                                                | Saját, minden JWT visszavonva             |
| POST /reservations                          | ReservationCreateRequest                                   | 201 ReservationResponse, Location az új foglalásra | Belépett                                  |
| GET /me/reservations                        | status?,page,size,sort                                     | PageResponse<ReservationSummaryResponse>           | Saját                                     |
| GET /reservations/{id}                      | –                                                          | ReservationResponse                                | Saját vagy kezelő; idegen404              |
| POST /reservations/{id}/cancel              | CancelRequest                                              | ReservationResponse                                | Saját, kezdés/átadás előtt                |
| GET /reservations/{id}/events               | page,size                                                  | PageResponse<AuditEventResponse>                   | Foglaláséval azonos                       |
| GET /equipment/{id}/availability            | from,to; max90nap                                          | AvailabilityResponse                               | Belépett; anonimizált                     |
| GET /equipment                              | startsAt?,endsAt? együtt; eredeti szűrők                   | PageResponse<EquipmentResponse>                    | Csak a kért időszakra foglalható eszközök |
| GET /management/reservations                | status?,requesterId?,equipmentId?,from?,to?,page,size,sort | PageResponse<ReservationSummaryResponse>           | MANAGER/ADMIN                             |
| POST /management/reservations/{id}/approve  | ApproveRequest                                             | ReservationResponse                                | MANAGER/ADMIN, nem saját                  |
| POST /management/reservations/{id}/reject   | RejectRequest                                              | ReservationResponse                                | MANAGER/ADMIN                             |
| POST /management/reservations/{id}/cancel   | CancelRequest, kötelező indok                              | ReservationResponse                                | MANAGER/ADMIN                             |
| POST /management/reservations/{id}/checkout | CheckoutRequest                                            | 201 LoanResponse, Location a kölcsönzésre          | MANAGER/ADMIN                             |
| GET /management/loans                       | overdue?,open?,page,size                                   | PageResponse<LoanResponse>                         | MANAGER/ADMIN                             |
| GET /management/loans/{id}                  | –                                                          | LoanResponse                                       | MANAGER/ADMIN                             |
| POST /management/loans/{id}/return          | ReturnRequest                                              | LoanResponse                                       | MANAGER/ADMIN                             |
| POST /management/equipment                  | EquipmentCreateRequest                                     | 201 EquipmentResponse                              | MANAGER/ADMIN                             |
| PUT /management/equipment/{id}              | EquipmentUpdateRequest                                     | EquipmentResponse                                  | MANAGER/ADMIN                             |
| POST /management/equipment/{id}/retire      | EquipmentRetireRequest                                     | EquipmentResponse                                  | MANAGER/ADMIN                             |
| GET /management/maintenance                 | equipmentId?,status?,page,size                             | PageResponse<MaintenanceResponse>                  | MANAGER/ADMIN                             |
| POST /management/maintenance                | MaintenanceCreateRequest                                   | 201 MaintenanceResponse                            | MANAGER/ADMIN                             |
| GET /management/maintenance/{id}            | –                                                          | MaintenanceResponse                                | MANAGER/ADMIN                             |
| POST /management/maintenance/{id}/close     | MaintenanceCloseRequest                                    | MaintenanceResponse                                | MANAGER/ADMIN                             |
| POST /management/maintenance/{id}/cancel    | Indok                                                      | MaintenanceResponse                                | MANAGER/ADMIN, még el nem kezdett         |
| PATCH /admin/users/{id}                     | AdminUserUpdateRequest                                     | AdminUserResponse                                  | ADMIN                                     |
| POST /admin/categories                      | CategorySaveRequest                                        | 201 CategoryResponse                               | ADMIN                                     |
| PUT /admin/categories/{id}                  | CategorySaveRequest                                        | CategoryResponse                                   | ADMIN                                     |
| DELETE /admin/categories/{id}               | –                                                          | 204; hivatkozás esetén409                          | ADMIN                                     |
| PATCH /admin/categories/{id}/active         | ActiveRequest                                              | 204                                                | ADMIN                                     |
| POST /admin/locations                       | LocationSaveRequest                                        | 201 LocationResponse                               | ADMIN                                     |
| PUT /admin/locations/{id}                   | LocationSaveRequest                                        | LocationResponse                                   | ADMIN                                     |
| DELETE /admin/locations/{id}                | –                                                          | 204; hivatkozás esetén409                          | ADMIN                                     |
| PATCH /admin/locations/{id}/active          | ActiveRequest                                              | 204                                                | ADMIN                                     |
| GET /dashboard/me                           | –                                                          | UserDashboardResponse                              | Saját                                     |
| GET /management/dashboard                   | –                                                          | ManagementDashboardResponse                        | MANAGER/ADMIN                             |

Későbbi listák rendezése is explicit fehérlistás lesz. Státuszparancsok nem PATCH status-ként működnek. Az endpoint elérése nem helyettesíti a service-beli tulajdonos- és állapotellenőrzést. PUT teljes mezőkészletet vár a módosítható mezőkből; a szerverkezelt mezők tiltottak.

## Példák

Regisztráció: {"displayName":"Minta Anna","email":"anna@example.test","password":"SajatJelszo2026!"}.

Belépés: {"email":"anna@example.test","password":"SajatJelszo2026!"}. További kérés fejléc: Authorization: Bearer <accessToken>.

Katalógus: GET /api/equipment?q=canon&categoryId=1&page=0&size=12&sort=name,asc.

Tervezett igénylés: {"equipmentId":1,"startsAt":"2026-11-12T09:00:00+01:00","endsAt":"2026-11-12T12:00:00+01:00","purpose":"Egyetemi interjúprojekt felvétele"}. A konkrét dátum mindig jövőbeli legyen.

## Ellenőrzés

A backend integrációs tesztjei valódi POST loginból kapott JWT-t használnak; nem csupán mockolt szerepköröket. Ellenőrzik a regisztrációt, normalizálást, hash-t, hibaválaszt, visszavonást, tiltást, szerepköröket, katalógust és szűrést. A következő egység elfogadási kapuja a két párhuzamos jóváhagyásból egy siker tesztje lesz.
