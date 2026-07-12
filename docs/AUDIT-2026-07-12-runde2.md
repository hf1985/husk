<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Husk-audit runde 2 (2026-07-12, via Note10) → release 0.9.29

Anden audit-runde efter 0.9.28, kørt **via Note10+** (den fulde rig: A12/DeX, kamera, token, adb-bro,
overbygning) med tre vinkler: (1) live-afprøve de rig-specifikke funktioner som spares ikke kunne teste,
(2) friske øjne på min egen 0.9.28-diff (indførte jeg selv bugs?), (3) dyb-audit af de filer der blev
let dækket i runde 1. Runde-1-loggen: `AUDIT-2026-07-12.md`.

## Live-verifikation via Note10 (0.9.28) – ingen regression fundet
Alt sundt på A12/DeX: **kamera** (/snapshot → JPEG, åbn/slip + FGS-elevering virker på A12),
**H.264** (/screen.mp4 → gyldig fMP4 `ftypisom`, codec `avc1.428020`), **MJPEG** (/screen → 588 KB),
**hardware** (battery/display/connectivity/sensors/brightness), **a11y** (PONG), **adb-bro** (brugt til
0.9.28-install), **DeX** (dexReconnect/dexCapable), **CSRF-forsvar** (cross-site→403, legit→200). Riggen
urørt (kamera slap, a11y re-bandt, DeX intakt). `/screen.jpg` gav 503 = display-0 statisk/slukket på
DeX-riggen (ingen MediaProjection-frame i 750 ms-vinduet) – IKKE en kode-regression (MJPEG gav frames;
samme adfærd som gammel kode).

## Implementeret i 0.9.29

### Regression fanget i selv-review (HIGH) – rettet
- **`acceptInstallConsent` læste en STALE `Rig.lastUpdate`** (min 0.9.28-C10-gate). `triggerUpdate`
  starter accept-tråden straks, men `Updater` (der nulstiller `lastUpdate="checking"`) er 1500 ms
  forsinket → ved et GENTAGET `/update` hvor forrige kørsel endte `"latest"`/`"ERR"` (normal-tilfældet)
  bail'ede gaten øjeblikkeligt → intet auto-tap → ubemandet self-update stallede. (Mine 0.9.28-live-tests
  ramte den ikke, fordi forrige tilstand var `""`/`"install requested"`.) **Fix (begge dele):** (1)
  `triggerUpdate` nulstiller `lastUpdate="checking"` SYNKRONT før accept-tråden; (2) `acceptInstallConsent`
  bail'er kun på latest/ERR EFTER at have set en in-progress-tilstand (`sawProgress`) – fjerner
  timing-afhængigheden helt.

### Deep-audit (LOW, men værd at rette)
- **`/vibrate?ms=` fik intet øvre loft** → `ms=2000000000` kørte motoren i dagevis. Nu clampet til 10 s.
- **Sensor-floats kunne emit'e `NaN`/`Infinity`** = ugyldig JSON → streng `JSON.parse`/`jq` kaster på
  hele svaret. Ny `fj()`-finite-guard → `null` i stedet (i `sensorsList` + `readSensor`).
- **`InstallReceiver` slugte install-FEJL lydløst** → en fjern-operator kunne ikke skelne succes fra
  fejl over `/flags`. Nu skrives fejl-status (`FAILURE/BLOCKED/CONFLICT/INCOMPATIBLE/…` + besked) til
  `Rig.lastUpdate` → headless self-update er nu observerbar remote.

## Verificeret CLEAN (ingen ændring) – fra begge agenter + egen gennemgang
- **Min 0.9.28-diff er ellers korrekt:** CSRF/originOk/readLineMax, ScreenService Bitmap-genbrug,
  H264 publish-lås (Client bruger identitet, så `removeAll` kan ikke ramme en genforbundet klient),
  CameraService FGS (≤A13 = uændret camera-type; `opening`-latch-fix bekræftet), Updater session,
  onMain keepFresh-gating, dfs-dybde, wakeScreen, Motion pr-kilde.
- **Fmp4Muxer** box-matematik bevist korrekt (moof=100 B, trun data_offset=108, splitAnnexB bounds-safe).
  **Hardware** ingen resource-leaks (mic slippes + tidsbundet 400 ms; sensor-listener afmeldes altid;
  battery = receiver-løs sticky-read). **DeXDetector/ScreenConsentActivity/InstallReceiver/MainActivity**
  lifecycle + exported-flader sunde.

## Bevidst UDELADT
- **Host-IP-literal-kravet (CSRF-fix) afviser MagicDNS-hostnavn-adgang** til 8090 (fx `http://note10:8090`).
  Alle klienter (harness, companion, ntfy, /control-viewer, docs) bruger den rå IP → ingen afhængighed.
  Behold IP-only (rebinding-tæt); dokumenteret her.
- **Slow/on-change-sensorer** (`temperature`/`pressure`/`humidity`) kan give `ERR no-reading` inden for
  1800 ms selv om de virker. Marginalt; dokumenteret frem for at hæve timeouten (koster responstid).

## Verifikation + udrulning
Build grøn (WSL/JDK21), signeret (SHA-256 `1b89a920…62af59`), begge `latest.json`-endpoints + F-Droid +
GitHub-release + xplat. **Special-test bestået: den rettede self-update-gate verificeret ved et GENTAGET
`/update` efter en `"latest"`-kørsel** – non-force → `"latest"` (intet tap), derefter force → hands-off
reinstall (8090 faldt + kom igen); præcis det tilfælde regressionen brød.

Udrulning: .102/A11 + Note10/A12 → 0.9.29 med a11y intakt (spare hands-off; Note10 rig-sikkert via adb).

### ⚠️ Kendt problem efter udrulning: .101 (A9) a11y afbundet
.101 (Sony 702SO/A9) endte efter 0.9.29-installen med **a11y afbundet** (8090/kamera kører, men 8127/`/rpc`
er nede). **En app-opdatering kan afbinde en aktiveret a11y-service på ældre Android** – A9 er udsat; A11
(.102) og A12 (Note10) re-bandt fint. **Spares mangler overbygningens a11y-vagthund** (som på Note10 re-
enabler a11y via adb efter opdatering). **Recovery = POWER-CYCLE .101** (a11y re-binder ved boot hvis stadig
i `enabled_accessibility_services`; ellers manuel re-enable under Tilgængelighed). Kan ikke fixes remote
uden adb (A9 mangler WD). **Fremtidig hærdning:** éngangs-fysisk-USB på A9-spare → adb → tilføj samme a11y-
vagthund, ELLER opdatér kun A9-spare når den kan genstartes bagefter. Ikke en kode-fejl i Husk – Android/
OEM-adfærd – men en reel drifts-konsekvens af at opdatere en a11y-app.
