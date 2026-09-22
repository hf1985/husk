# P_app_husk – agent-kontekst

> ## ⛔ RELEASE-PLIGT – en kodeændring er IKKE færdig før den er UDGIVET
>
> **ENHVER ændring af app-koden (`app/src/**`) SKAL udgives som en fuld, signeret release,
> FØR opgaven regnes som færdig.** Committet-men-ikke-udgivet = flåden (de fysiske telefoner)
> kører stadig den GAMLE kode; det er en UFÆRDIG opgave. Det gælder også autonome/uovervågede
> agenter (fx P_auto-optimering): stop ALDRIG ved commit.
>
> ⚠️ **Fra 1.1 har appen INGEN indbygget updater** (F-Droid-fund 3-9, 15-09-2026: en app der henter
> og installerer sine egne opdateringer omgår butikkens signering og review). Flåden opgraderes
> derefter gennem **F-Droid-klienten** eller **`adb install` over husets Termux-ADB**.
> **TRE flader hedder næsten det samme, og de har hver sin ejer. Hold dem fra hinanden:**
>
> | Flade | Hvem læser den | Hvornår kan den gå væk |
> |---|---|---|
> | `HUSK_VERSION_NAME`/`_CODE` i `P_xplat/hosting/app.py` | websiden, og `pc/check-api-parity.sh`, som sammenligner `HUSK_VERSION_CODE` med `app/build.gradle` | aldrig – den er release-gaten |
> | `https://xplat.co/husk/latest.json` | genereres af de konstanter; **læses desuden af 1.0-flådens updater som FØRSTE kilde** | når flåden er på 1.1 mister den sin app-læser, men bliver som websidens versionsvisning |
> | `latest.json` **i dette repo** | KUN 1.0-flådens updater, som fallback når xplat.co ikke svarer | når flåden er på 1.1 har den **ingen læser tilbage** og kan slettes |
>
> ⚠️ `check-api-parity.sh` læser **ikke** `latest.json` – den sammenligner `app/build.gradle` med
> `P_xplat`s `HUSK_VERSION_CODE`.
>
> ⛔ **Undtagelsen udløber:** 1.0-telefoner HAR stadig updateren. Begge `latest.json`-flader skal
> derfor stå rigtigt indtil hele flåden er på 1.1 – det er den sidste opgradering der kan ske i
> appen. Rør ikke repoets `latest.json`s FORM før da, og slet den først når `/info` på hver enhed
> viser 52 eller derover.
>
> **Release-huskeliste** (fuld procedure: `docs/BUILD.md` §4–7):
> 1. Bump `versionCode` + `versionName` – ÉT sted: `app/build.gradle`.
> 2. Byg `assembleRelease` i WSL + signér med release-keystoren (alias `husk`; adgangskode fra vaulten).
> 3. Opdatér repo'ets `latest.json` (versionsfelter + `apk`-URL). ⛔ **Læg ALDRIG APK'en i repoet
>    igen** – binære filer i kildetræet kan ikke revideres, `.gitignore` dækker `*.apk`, og den
>    signerede APK hører i GitHub-releasen.
> 3b. **Skriv `fastlane/metadata/android/{en-US,da}/changelogs/<versionCode>.txt`** (maks 500
>     tegn). Med `AutoUpdateMode: Version` lander en release UDEN denne fil med et tomt
>     »What's New« i F-Droid.
> 4. Opdatér `fdroid/co.xplat.husk.yml` og spejl den op i forken.
>    ⛔ **Både !40810 (15-09) og !49350 (20-09) er MERGET, så der er INGEN levende MR: en opdatering kræver en NY MR fra en gren frisk fra upstream master.**
>    Værktøjerne kræver `--adversarisk` og har et tegn-loft.
> 5. Opdatér HUSK-konstanterne (`HUSK_VERSION_*`) i `P_xplat/hosting/app.py` **OG deploy xplat.co**.
> 6. **API-DOK-GATE (obligatorisk):** ændrer releasen endpoints/params/respons/adgangsmodel? Ajourfør
>    `HUSK_API`-kataloget (+ OpenAPI-beskrivelsen) i `P_xplat/hosting/app.py` (driver BÅDE `/husk/api`
>    OG `/husk/openapi.json`), deploy xplat, og kør **`bash pc/check-api-parity.sh` → skal være GRØN**
>    (fejler ved udokumenterede/stale endpoints + versionCode-drift). Verificér `/husk/api` live.
> 7. Commit + tag `v<versionName>` + push; GitHub-release med den signerede APK. ⛔ **Tagget SKAL
>    sidde på PRÆCIS byggecommiten**, og tag og asset udledes af `versionName`, ikke af semver:
>    F-Droids `Binaries:` er `.../download/v%v/husk-v%v.apk`. Er versionName `1.2`, hedder tagget
>    `v1.2` og assettet `husk-v1.2.apk` – ellers henter F-Droid en 404 og indsendelsen brækker.
> 8. Verificér: BÅDE `https://xplat.co/husk/latest.json` OG
>    `https://raw.githubusercontent.com/hf1985/husk/main/latest.json` viser den nye `versionCode` og
>    peger på en APK der FAKTISK kan hentes, og F-Droid/GitLab-pipelinen er GRØN. En 404 bag
>    `apk`-feltet er en fejlet releaseprøve, ikke en skønhedsfejl.
>
> **Definition af færdig:** begge `latest.json`-endpoints viser den nye version og en hentbar APK,
> F-Droid-pipelinen er grøn, **OG `pc/check-api-parity.sh` er grøn + `/husk/api` er ajour** (trin 6).
> Før ALT dette er opgaven ÅBEN – uanset hvor grøn builden er lokalt.
>
> **De hændelser der er grunden til hvert af kravene ovenfor** – den ureleasede XSS-rettelse, den
> glemte API-dok, den flyttede tag-commit og den falske påstand om `check-api-parity.sh`s læsning
> af `latest.json` – står i `docs/versionshistorik.md` → »Hvorfor release-pligten ser sådan ud«.
> Påstanden bor her, beviset derovre; filen auto-loades i hver agents kontekst.

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver **xplat**) er den publicerede FOSS-app afledt af
Note10-rig'en: gør en gammel Android-telefon til fjernstyret kamera + accessibility-automationsmotor
+ scrcpy/adb-bro over eget Tailscale-net, uden root. Udgives på **F-Droid** + GitHub-releases
(`hf1985/husk`) + `xplat.co/husk`. **Pure framework** (ingen AndroidX, ingen deps) → nem
F-Droid-build. Kilderne ligger i `app/src/main/java/co/xplat/husk/`.

## Rolle i økosystemet (Husk er MOTOREN, de andre bygger ovenpå)
- **`P_add-on_phone-transport`** – delt transport (SSH+Termux+Tailscale + boot). Husk importerer
  intet derfra; transporten er hvordan man NÅR telefonen.
- **`husk-overbygning`** (privat, `$env:USERPROFILE\repos\husk-overbygning`) – ubemandet
  Discord-mødekamera. Bruger 8127-RPC + kamera, og har INGEN motor-/kamera-/WD-logik.
- **`P_app_phone-devbox`** (8127 passivt) og **`P_kontor`** (office-consumer).

## Komponenter + porte (én proces)
- **8127** `RigAccessibilityService` – loopback-RPC, linjebaseret (tap/swipe/find/state/dump/launch/
  text/global + in-process WD-recovery). Tom `onAccessibilityEvent`; noder hentes on-demand.
- **8090** `ControlServer` – HTTP på `0.0.0.0` bag kilde-IP-ACL: kamera, skærm, input-proxy til 8127,
  hardware, mgmt og motion. Hostes af CameraService/ScreenService (`Rig.ensureControlServer`),
  proces-singleton. ⛔ **Endpoint-kataloget er `HUSK_API` i `P_xplat/hosting/app.py`**, ikke denne
  fil: det driver `/husk/api` + `/husk/openapi.json`, og `check-api-parity.sh` gater det.
- **15557** `AdbForward` – app-native scrcpy/adb-bro over Tailscale (Termux-uafhængig).
- `BootReceiver` (boot + `MY_PACKAGE_REPLACED`), `CameraService`, `ScreenService`, `MainActivity`,
  `Motion`/`Ntfy`. Selv-opdaterings-klasserne er slettet i 1.1.

## MUST-NOT-REGRESS-invarianter (LÆS docs/YDELSE-OG-DRIFT.md før du rører Screen/Camera/a11y)
Husk er idle det meste af tiden og må KUN bruge ressourcer on-demand. Alle blev indført efter
konkrete CPU- og frys-regressioner – **rul dem ALDRIG tilbage.** Her står påstanden; koden, målingen
og postmortem'et står i `docs/YDELSE-OG-DRIFT.md` §1.
- **A – skærm-streaming er DOVEN:** `ScreenService.onFrame` koder kun JPEG når en `/screen`-klient
  ser med, eller motion er TIL.
- **B – a11y-masken er SMAL:** kun `typeWindowStateChanged`, udvidet i 5 s efter en a11y-op via
  `keepCacheFresh()`. Sæt ALDRIG `typeWindowContentChanged` permanent.
- **C – kameraet er DOVENT, og Husk EVICTER aldrig:** enheden åbnes kun når en forbruger bruger den
  OG den er LEDIG (`AvailabilityCallback`); ingen forbruger → slip den. **Præcis én doven løkke** –
  det var en ekstra `demandCheck`-løkke pr. kameraside-skift 1.2 rettede.
- **D er FJERNET i v0.9.22 og må ikke komme igen:** **gen-indfør ALDRIG en cross-display
  launch/bounce**. Den triggede Samsungs »genstart på anden skærm«-dialog og crashede scrcpy og
  Discord på DeX; kamera-frysen dækkes af C alene.

## Build, signering, release
Kanonisk build = Gradle `assembleRelease` i WSL (`~/android-build`, env21.sh = JDK21+SDK);
een-kommando `gradle-build.sh`. Fuld procedure: `docs/BUILD.md`.
**Byg IKKE via `/mnt/g`** (Drive i WSL flaky) – stage til `C:` og lad WSL læse `/mnt/c`, eller kald
med `MSYS_NO_PATHCONV=1`. ⛔ **`wsl.exe -- bash -c '<streng>'` ekspanderer strengen ÉN GANG FOR
MEGET**, i et miljø hvor strengens egne tildelinger endnu ikke er kørt: `source env21.sh;
echo $ANDROID_HOME` gav TOM, og `local.properties` blev skrevet tom. Enkeltcitater hjælper ikke.
**Læg WSL-trin i en FIL og kør filen.** Målt 2026-09-20, se måleregel 474.
**Signeringsnøglen** (alias `husk`, SHA-256 `96195cfd…c17d`) og dens kodeord ligger i **vaulten** som
login-item `Husk release-signeringsnoegle (keystore husk-release.jks, base64)`; arbejdskopi i WSL
`~/android-build/husk-signing/husk-release.jks`. Kodeordet står ALDRIG i en fil i repoet og aldrig i
argv. `.gitignore` dækker `*.keystore` OG `*.jks`. Nøgleskiftet: `docs/BUILD.md` §5.
Nuværende: **1.2 / versionCode 53** (2026-09-19, `hfs-dell`, tagget på byggecommiten `390d9c5e`).
1.2 retter én fejl i den udgivne 1.1: `requestFront` startede en ekstra 1-sekunds-løkke pr.
kameraside-skift, i strid med invariant C. `ControlServer.java` er byte-uændret fra `v1.1`, så
API-fladen er den samme, og F-Droids CI har reproduceret 52 og 53 mod referencebinæren.
Alle tidligere udgaver: `docs/versionshistorik.md`.

## Flåde, tailnet-transport og deploy til en KØRENDE rig
Fuld tekst: `docs/fleet-tailnet-transport.md` og `docs/YDELSE-OG-DRIFT.md` §3.
- **Husk er selv en tailnet-tjeneste:** 8090 + 15557 binder `0.0.0.0` bag kilde-IP-ACL
  (`Net.peerAllowed`), så enhver peer styrer en enhed DIREKTE, **uden Termux**.
- **Deploy til den kørende rig:** `adb install -r <apk>`, derefter `adb reboot`. **Launch IKKE
  `MainActivity` via `am start`**, heller ikke som test: det slår midlertidigt a11y fra.
- **Efter enhver opdatering SKAL `/snapshot` OG `/screen.jpg` efterprøves pr. enhed** – porten
  kommer op uanset, så `/healthz` kan være grøn mens kameraet ligger nede (måleregel 422).
- **`wake` FØRST på en spare:** en sovende skærm får a11y til at se kun navbaren og gestus til at
  svare `ERR cancelled`, hvilket engang blev fejltolket som en død motor. Harness: `pc/spare.*`.
- ⛔ **Flåden er IKKE ensartet.** Enheder og version pr. enhed står i `FORTSÆT-HER.md`; to kilder
  til ét tal driver fra hinanden. Den gamle `/update?force=1`-deployvej er FJERNET i 1.1.

## Faste regler
- **Dansk** i docs/kommentarer/commits med ÆGTE æ/ø/å (husets almene regel) – men **ASCII-ificér
  ALDRIG et regex der matcher dansk system-UI**; dér skal de rigtige tegn stå.
- **Deploy-politik:** grønt testede ændringer køres live straks; rul tilbage ved fejl.
  Destruktivt/irreversibelt bekræftes først.
- Gamle navne (`note10-app`, `com.khfrb.note10`, `dextap`, `DexRPC`, `meeting-camera`, `M650`) er
  renset – brug dem ikke.

## Docs
`README.md` (overblik), `FORTSÆT-HER.md` (udestående lige nu), `docs/BUILD.md` (build/release),
`docs/YDELSE-OG-DRIFT.md` (invarianter + diagnostik), `docs/fleet-tailnet-transport.md` (flåde),
`docs/versionshistorik.md` (historik), `fdroid/` (F-Droid-metadata). I `pc/`:
`check-api-parity.sh` (release-gate), `spare.*` (flåde-harness), `husk-companion.ps1` (scrcpy),
`udadvendt.py` (gaten om udadvendt tekst) + `fdroid-mr-comment.py`/`fdroid-fork-update.py`.
