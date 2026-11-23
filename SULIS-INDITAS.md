# EszközPont indítása új Windows gépen

Ez az útmutató önállóan használható a projekt GitHubról letöltött forrásával. Az első indítás után a böngészőben működik a felület, a háttérben pedig Spring Boot backend és tartós PostgreSQL-adatbázis fut. A parancsokat PowerShellben, például az IntelliJ Terminal ablakában futtasd.

## 1. Amit indulás előtt készíts elő

- Az otthoni legutóbbi kódmódosításokat és az új forrásfájlokat commitold és pushold. A GitHubon ellenőrizd, hogy a kívánt változat látható. Egy fájl attól kerül be a klónozásba, hogy benne van a feltöltött commitban.
- Ezt az egy útmutatót mentsd el külön is, például pendrive-ra vagy a saját tárhelyedre. Ha a fájlt kihagyod a repóból, a másik gépre külön kell magaddal vinned.
- Az első indításhoz internet kell. A Maven, a frontendcsomagok és a natív PostgreSQL-futtatókörnyezet letöltődnek. Ez több percig is eltarthat.
- Az útmutató Windows x64 géphez készült. Az iskolai telepítési és futtatási jogosultságokat érdemes előre kipróbálni ugyanazon a gépen.

## 2. Programok ellenőrzése és telepítése

Nyiss PowerShellt, és egyenként futtasd.

```powershell
git --version
java -version
javac -version
node --version
npm.cmd --version
pnpm.cmd --version
```

| Eszköz | Szükséges változat | Szerepe |
| --- | --- | --- |
| Git | Telepített Windows-verzió | A forrás letöltése. A GitHub ZIP-letöltésével is elhozhatod a forrást. |
| Java JDK | 25 vagy újabb | Backend fordítása és futtatása. A JDK 25 megfelelő választás. |
| Node.js | 22.13 vagy újabb | Frontendeszközök futtatása. A Node.js 24 LTS megfelelő választás. |
| npm | A Node.js telepítőjével érkezik | A pnpm telepítése. |
| pnpm | 11.25.0 | A projekt által rögzített frontendcsomag-kezelő. |

### Java

A hivatalos Adoptium oldalon válassz **JDK 25 → Windows → x64 → JDK** csomagot. Telepítő használatakor engedélyezd a JAVA_HOME és PATH beállítását, ha felkínálja. Telepítés után nyiss új terminált. [Temurin JDK letöltése](https://adoptium.net/temurin/releases/?version=25)

Ha az IntelliJ már letöltött megfelelő JDK-t, annak könyvtárát is használhatod. Általában a felhasználód `.jdks` mappájában van. A következő sorban a tényleges JDK-könyvtárat add meg, amelyben a `bin/java.exe` és a `bin/javac.exe` található.

```powershell
# A saját gépen létező útvonalra írd át!
$env:JAVA_HOME = 'C:\Users\SAJAT_FELHASZNALO\.jdks\SAJAT_JDK_25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
```

Ez a beállítás az adott terminálra érvényes. Ha másik terminálból indítod a backendet, ott is állítsd be, vagy használd a telepítő tartós beállítását. A `JAVA_HOME` a JDK gyökerére mutasson.

### Node.js és pnpm

A hivatalos Node.js letöltőoldalról telepíts Windows x64 Node.js 24 LTS-t. A telepítővel az npm is felkerül. Nyiss új terminált, és ellenőrizd a `node --version` és `npm.cmd --version` eredményét. [Node.js letöltése](https://nodejs.org/en/download)

A pnpm telepítése és ellenőrzése.

```powershell
npm.cmd install --global pnpm@11.25.0
pnpm.cmd --version
```

A `.cmd` végződés Windowson közvetlenül a parancsindítót választja. Ha egy már telepített pnpm máshonnan is elérhető, a `Get-Command pnpm -All` megmutatja a találatokat. [pnpm telepítési dokumentáció](https://pnpm.io/installation)

Ha a globális telepítés jogosultsági hibával áll le, saját felhasználói könyvtárba telepítheted.

```powershell
npm.cmd install --global --prefix "$env:USERPROFILE\eszkozpont-tools" pnpm@11.25.0
$env:Path = "$env:USERPROFILE\eszkozpont-tools;$env:Path"
pnpm.cmd --version
```

Az utolsó PATH-beállítást minden olyan új terminálban végezd el, ahonnan ezt a saját telepítést használod.

Ha a gép szabályzata magát a telepítést, a letöltést vagy a futtatást tiltja, az oktatótól vagy a rendszergazdától kell hozzáférést kérni.

### Git

Ha hiányzik a Git, telepítsd a hivatalos Windows-csomagot, majd nyiss új terminált. Alternatívaként a GitHub **Code → Download ZIP** gombjával töltsd le és csomagold ki a projektet. [Git for Windows letöltése](https://git-scm.com/install/windows)

## 3. A projekt letöltése

Egy olyan saját könyvtárban futtasd, ahová írhatsz.

```powershell
git clone https://github.com/seboktibor2005/webfejlesztes.git
cd webfejlesztes
Get-ChildItem
```

A projekt gyökerében a `backend`, `frontend` és `tools` mappát kell látnod. ZIP-letöltésnél lépj be a kicsomagolt projekt gyökérmappájába.

A szükséges programok, például az IntelliJ saját Java-futtatókörnyezete és a terminálban használt JDK külön is beállíthatók. A parancssori indítás szempontjából az előző pontban ellenőrzött JDK számít.

## 4. Első indítás három lépésben

### A. Frontendfüggőségek telepítése

A projekt gyökérmappájában.

```powershell
.\tools\run.ps1 -Task Install
```

Várd meg, amíg hibamentesen visszatér a parancssor. A letöltés az első alkalommal időigényesebb.

### B. Backend és adatbázis

Az első terminálban, a projekt gyökeréből.

```powershell
$env:DEMO_DATA_ENABLED = 'true'
.\tools\run.ps1 -Task Backend
```

Az indító a Maven Wrapperrel letölti a Java-függőségeket, fordítja a projektet, elindítja a helyi PostgreSQL-t és a Spring Boot alkalmazást. Induláskor a Flyway létrehozza a sémát. Üres adatbázisban a demófeltöltés létrehozza a bemutatófiókokat és az eszközöket. Az aláíráshoz használt helyi JWT-kulcsot az indító generálja.

Várd meg a `Started LocalDevelopment` és a 9090-es porton elindult Tomcat üzenetét. Ez a terminál a háttérben futva marad.

### C. Frontend

Nyiss egy második terminált, és ott is lépj a projekt gyökérmappájába.

```powershell
.\tools\run.ps1 -Task Frontend
```

Várd meg a Vite által kiírt Local címet. Nyisd meg a böngészőben a `http://127.0.0.1:5173` oldalt.

| Szolgáltatás | Alapértelmezett cím |
| --- | --- |
| Frontend | http://127.0.0.1:5173 |
| Backend API | http://127.0.0.1:9090/api |
| Helyi Swagger | http://127.0.0.1:9090/swagger-ui/index.html |
| PostgreSQL | 127.0.0.1:55432 |

A backend gyökércíme helyett a Swagger oldal megnyitásával ellenőrizd a webes indulást. A `/api/me` cím token nélkül 401-et ad, ez a hitelesítési védelem működését jelzi.

A két szolgáltatás leállításához a saját termináljukban nyomj `Ctrl+C`-t. Következő indításkor a B és C lépés kell. Függőségváltozás után az A lépést is ismételd meg.

## 5. Adatbázis és demóbelépés

A helyi PostgreSQL adatkönyvtára a projektben a `.local/postgres`. Új gépen, új klónnál az indító létrehozza. A backenddel együtt indul. A Git a saját gépeden korábban mentett foglalásokat és a helyi kulcsot kihagyja, ezért az új gépen új demóadatbázisod lesz.

| Szerepkör | E-mail cím | Helyi demójelszó |
| --- | --- | --- |
| Hallgató | anna@eszkozpont.test | EszkozPont2026! |
| Második hallgató | bence@eszkozpont.test | EszkozPont2026! |
| Eszközfelelős | kezelo@eszkozpont.test | EszkozPont2026! |
| Adminisztrátor | admin@eszkozpont.test | EszkozPont2026! |

A saját helyi adatkönyvtár újraindítás után is megmarad. A feltöltő csak teljesen üres adatbázisba ír. A foglalásokat jövőbeli időpontra add fel, 1 óra és 14 nap közötti időtartammal. A cél legalább egy nem üres karakter legyen.

### IntelliJ kapcsolat

Először induljon el a backend. Az IntelliJ-ben nyisd meg a **View → Tool Windows → Database** ablakot, és válaszd a **+ → Data Source → PostgreSQL** lehetőséget.

| Beállítás | Érték |
| --- | --- |
| Name | EszközPont helyi |
| Host | 127.0.0.1 |
| Port | 55432 |
| Database | postgres |
| User | postgres |
| Password | postgres |
| JDBC URL | jdbc:postgresql://127.0.0.1:55432/postgres |

Ha szükséges, kattints a **Download missing driver files** lehetőségre, majd a **Test Connection** gombra. A **Schemas** fülön válaszd a `public` sémát. Az **OK** után bontsd ki a kapcsolatot és a táblákat, majd dupla kattintással nyisd meg például az `app_user` táblát.

Ellenőrző lekérdezés az adatbázis-konzolban.

```sql
SELECT id, display_name, email, role, enabled
FROM app_user
ORDER BY id;
```

Ha a Database ablak hiányzik, a **Settings → Plugins** alatt ellenőrizd a **Database Tools and SQL** bővítményt. Az elérhető funkciók az IntelliJ-verziótól és az Ultimate-hozzáféréstől is függnek. A webalkalmazás a két terminálból ettől függetlenül indítható. [IntelliJ PostgreSQL útmutató](https://www.jetbrains.com/help/idea/postgresql.html)

## 6. Ha a PowerShell nem indítja a szkriptet

A `running scripts is disabled` vagy `cannot be loaded` hiba a szkriptfuttatás beállítására utalhat. A következő közvetlen parancsok a Windows `.cmd` indítóit használják.

A frontend telepítése és indítása a projekt gyökérmappájából, egy terminálban.

```powershell
cd frontend
pnpm.cmd install --frozen-lockfile
pnpm.cmd run dev
```

A backend egy másik terminálban, szintén a projekt gyökérmappájából indulva.

```powershell
cd backend
$env:DEMO_DATA_ENABLED = 'true'
.\mvnw.cmd test-compile exec:java "-Dexec.mainClass=hu.eszkozpont.LocalDevelopment" "-Dexec.classpathScope=test"
```

A Maven argumentumok körüli idézőjeleket másold át. A backendparancsot a `backend` mappából futtasd, mert ehhez igazodik a helyi adatkönyvtár elérési útja. Ha a hiba kifejezetten iskolai házirendre vagy végrehajtható fájl tiltására utal, az oktató vagy a rendszergazda tudja engedélyezni a futtatást.

## 7. Portütközés felismerése

A `Port … is already in use` vagy `Address already in use` azt jelenti, hogy az adott porton már fut egy program. Új indítás előtt próbáld megnyitni a felületet és a Swaggert. Lehet, hogy az előző indításod még fut.

Egy harmadik PowerShell-terminálban megkeresheted a figyelő folyamatokat.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5173,9090,55432 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

Az `OwningProcess` a folyamat száma. A következő példában a `12345` helyére a tényleges számot írd.

```powershell
Get-Process -Id 12345
```

Ha a saját korábbi példányod, keresd meg a terminálját és állítsd le `Ctrl+C`-vel, majd próbáld újra. Ha az oktató gépének másik szolgáltatása használja a portot, az alábbi portváltást alkalmazhatod.

### A frontend 5173 helyett 5174 porton

Az első terminálban, a backend indítása előtt add meg az új frontend eredetét.

```powershell
$env:FRONTEND_ORIGIN = 'http://localhost:5174,http://127.0.0.1:5174'
.\tools\run.ps1 -Task Backend
```

A második terminálban a projekt gyökeréből.

```powershell
cd frontend
pnpm.cmd run dev --port 5174
```

A felület ekkor `http://127.0.0.1:5174`. Az alap Vite-konfiguráció szigorúan kéri az 5173-as portot, ezért portütközéskor ezt a külön indítóparancsot használd.

### A backend 9090 helyett 9091 porton

A backend termináljában, a projekt gyökerében.

```powershell
$env:SERVER_PORT = '9091'
.\tools\run.ps1 -Task Backend
```

A `frontend/vite.config.js` fájlban a proxy célját is írd át.

```js
proxy: { "/api": "http://127.0.0.1:9091" },
```

Indítsd újra a frontendet. A Swagger új címe `http://127.0.0.1:9091/swagger-ui/index.html`. A böngészős frontend címe ettől még a választott frontendport marad.

### A PostgreSQL 55432 helyett 55433 porton

Ha a portot másik adatbázis használja, a helyi indítóban ezt a fájlt nyisd meg.

```text
backend/src/test/java/hu/eszkozpont/LocalDevelopment.java
```

A `setPort(55432)` hívást írd át `setPort(55433)` értékre, majd indítsd újra a backendet a szokásos paranccsal. Az indító a PostgreSQL-től kapott új JDBC URL-t használja a backend kapcsolatához. IntelliJ-ben a Port mezőt szintén 55433-ra kell állítani.

Ha a hiba `could not lock ... epg-lock`, előbb ellenőrizd a korábbi backend és az adatbázis futását. Ugyanazt az adatkönyvtárat egyszerre egy adatbázis használhatja, ezen a portváltás önmagában nem segít. A működő saját példányt használd vagy szabályosan állítsd le.

A három port egymástól független. Ha több portot változtatsz, alkalmazd együtt az érintett beállításokat. A környezeti változók az adott terminálra vonatkoznak. A forrásfájlban végzett portmódosítások a fájl visszaállításáig érvényesek.

## 8. Gyakori hibák és a következő lépés

| Hiba | Mit ellenőrizz vagy tegyél? |
| --- | --- |
| `java` vagy `javac` nem található | Telepíts JDK 25-öt, vagy állítsd be a tényleges JDK-könyvtárat JAVA_HOME-ként és a bin könyvtárát a PATH-ba. |
| `release version 25 not supported` vagy `invalid target release` | A Maven régebbi Java fordítót használ. Állítsd be a JAVA_HOME-ot, majd a backend mappában a `.\mvnw.cmd -version` paranccsal ellenőrizd a Maven által használt Javát. |
| `node`, `npm` vagy `pnpm` nem található | Telepítés után nyiss új terminált. Saját prefixbe telepített pnpm esetén add hozzá az útvonalát az adott terminál PATH-jához. |
| npm/pnpm `EACCES`, `EPERM` | A célkönyvtárhoz nincs megfelelő hozzáférés, vagy egy fájlt másik folyamat zárol. Használj saját írható projektmappát és a saját felhasználói pnpm-telepítést. |
| Letöltési hiba, `ETIMEDOUT`, proxy vagy tanúsítványhiba | Ellenőrizd az internetet és az iskolai proxybeállítást. A rendszergazda tudja megadni a hálózathoz szükséges beállításokat. A fő letöltési helyek a Maven Central és az npm registry. |
| A felület megnyílik, az API nem érhető el | Nézd meg a backend terminálját. Ha portot módosítottál, a Vite proxyja ugyanarra mutasson. |
| CORS-hiba | A backend FRONTEND_ORIGIN értékében szerepeljen a böngészőben ténylegesen használt séma, gépnév és port. Alap local módban a localhost és a 127.0.0.1 is engedélyezett az 5173-as porton. |
| IntelliJ Test Connection sikertelen | Induljon a backend és vele a PostgreSQL. A helyi adatbázis portja alapból 55432, a neve postgres. |
| PostgreSQL bináris indulása tiltott | Olvasd el a backend részletes hibáját. Iskolai vírusvédelem vagy futtatási szabályzat esetén kérj engedélyezést az oktatótól vagy rendszergazdától. |
| A demóbelépés sikertelen | Ellenőrizd a jelszót és a DEMO_DATA_ENABLED kapcsolót. A feltöltés csak üres adatbázisban fut. Már részben feltöltött adatbázisban a kapcsoló nem hozza létre utólag a hiányzó fiókokat. |
| A jóváhagyás vagy átadás 409-et ad | Nézd meg az üzleti hiba szövegét. Jóváhagyás a kezdés előtt, átadás a jóváhagyott időablakon belül történhet. Másik foglalás, nyitott kölcsönzés vagy javítás is blokkolhat. |
| Oldalfrissítés után belépést kér | A token memóriában él, így teljes újratöltéskor újra be kell lépned. |
| Flyway checksum vagy validációs hiba | A kód és a már alkalmazott migrációk eltérhetnek. Őrizd meg az adatbázist és a hibaüzenetet, és ellenőrizd a letöltött kód változatát. |

## 9. Demókapcsolók

A demóadatok feltöltése a backend termináljában, indítás előtt kapcsolható.

```powershell
$env:DEMO_DATA_ENABLED = 'true'
# Kikapcsoláshoz az érték 'false'
```

A kikapcsolás a kezdeti feltöltést tiltja. A már mentett adatok az adatbázisban maradnak.

A belépési oldalon a demófiókok felsorolása a frontend termináljában, indítás előtt rejthető el.

```powershell
$env:VITE_SHOW_DEMO_ACCOUNTS = 'false'
# Megjelenítéshez az érték 'true'
```

A kapcsoló módosítása után indítsd újra az érintett szolgáltatást. A gyökérben lévő `.env.example` fájl beállításminta. A fenti PowerShell-parancsok a tényleges folyamatkörnyezetet állítják be.

## 10. Rövid indulási ellenőrzés a bemutató előtt

1. A szükséges forrásfájlok megérkeztek a GitHubról.
2. A Maven által használt Java legalább 25, a Node legalább 22.13, a pnpm 11.25.0.
3. A frontendfüggőségek telepítése sikerült, a backend elindult és elérhető a Swagger.
4. A böngészőben működik a hallgatói és kezelői belépés, látszanak az eszközök.
5. Az átadás bemutatásához készíts elő egy jóváhagyott foglalást, amelynek időablaka a bemutató alatt tart. Az igénylést és jóváhagyást még a kezdet előtt végezd el.
6. A két szolgáltatás terminálja futva marad, az esetleges portváltások összeillenek.

Ha van idő a teljes ellenőrzésre, a projekt gyökeréből a következő parancs futtatja a teszteket. A backendtesztek saját ideiglenes PostgreSQL-adatbázist használnak.

```powershell
.\tools\run.ps1 -Task Test
```
