# P_app_husk – agent-kontekst

> ## ⛔ RELEASE-PLIGT – en kodeændring er IKKE færdig før den er UDGIVET
>
> **ENHVER ændring af app-koden (`app/src/**`) SKAL udgives som en fuld, signeret release,
> FØR opgaven regnes som færdig.** Committet-men-ikke-udgivet = flåden (de fysiske telefoner)
> kører stadig den GAMLE kode; det er en UFÆRDIG opgave. Det gælder også autonome/uovervågede
> agenter (fx P_auto-optimering): stop ALDRIG ved commit. (Senest brændt: XSS-sikkerhedsfixet
> `e999ae4` blev committet men aldrig releaset – alle enheder kørte videre på 0.9.25 uden fixet.)
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
> `P_xplat`s `HUSK_VERSION_CODE`. Her stod indtil 2026-09-19 at gaten var en af `latest.json`s
> læsere; det var falsk og ville have holdt en fil i live på en grund den ikke har.
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
>    igen** – `husk-latest.apk` er fjernet i 1.1 (F-Droid-fund 2: binære filer i kildetræet kan ikke
>    revideres), og `.gitignore` dækker nu `*.apk`. Den signerede APK hører i GitHub-releasen.
> 3b. **Skriv `fastlane/metadata/android/{en-US,da}/changelogs/<versionCode>.txt`** (maks 500
>     tegn). Med `AutoUpdateMode: Version` lander en release UDEN denne fil med et tomt
>     »What's New« i F-Droid – påpeget af F-Droid-testeren 06-09-2026.
> 4. Opdatér `fdroid/co.xplat.husk.yml` (+ fork-metadata, MR !40810).
> 5. Opdatér HUSK-konstanterne (`HUSK_VERSION_*`) i `P_xplat/hosting/app.py` **OG deploy xplat.co**.
> 6. **API-DOK-GATE (obligatorisk):** ændrer releasen endpoints/params/respons/adgangsmodel? Ajourfør
>    `HUSK_API`-kataloget (+ OpenAPI-beskrivelsen) i `P_xplat/hosting/app.py` (driver BÅDE `/husk/api`
>    OG `/husk/openapi.json`), deploy xplat, og kør **`bash pc/check-api-parity.sh` → skal være GRØN**
>    (fejler ved udokumenterede/stale endpoints + versionCode-drift). Verificér `/husk/api` live.
> 7. Commit + tag `v<versionName>` + push; GitHub-release med den signerede APK.
>    ⚠️ **Tagget og assettet udledes af `versionName`, ikke af semver.** F-Droids `Binaries:`-URL er
>    `.../download/v%v/husk-v%v.apk` med `%v` = `versionName`. Er versionName `1.0`, SKAL tagget hedde
>    `v1.0` (ikke `v1.0.0`) og assettet `husk-v1.0.apk`, ellers henter F-Droid en 404 og indsendelsen brækker.
> 8. Verificér: BÅDE `https://xplat.co/husk/latest.json` OG
>    `https://raw.githubusercontent.com/hf1985/husk/main/latest.json` viser den nye
>    `versionCode` og peger på en APK der FAKTISK kan hentes, og F-Droid/GitLab-pipelinen er GRØN.
>    De to flader er versionsvisning fra 1.1 og frem, men de skal stadig stemme – en 404 bag
>    `apk`-feltet er en fejlet releaseprøve, ikke en kosmetisk skønhedsfejl.
>
> **Definition af færdig:** begge `latest.json`-endpoints viser den nye version og en hentbar APK,
> F-Droid-pipelinen er grøn, **OG `pc/check-api-parity.sh` er grøn + `/husk/api` er ajour** (trin 6).
> Før ALT dette er opgaven ÅBEN – uanset hvor grøn builden er lokalt. (Erfaring 2026-07-12: en
> release bumpede versionen men glemte 5 nye endpoints + CSRF-modellen i `/husk/api`; gaten fanger
> netop dét.)
> (Flåden opgraderes derefter gennem F-Droid-klienten eller `adb install` over husets Termux-ADB –
> aldrig on-phone build. Her stod indtil 1.1 »via in-app Updater«; den findes ikke mere.)

> **Miljø-regel (Windows/PowerShell→ssh):** sender du en `ssh`/`scp`/`mysql -e`-kommando med `(` `)` `$()` backtick, linjeskift eller `"`? Inline den IKKE – PowerShell spiser embedded quotes, så metakarakterer brækker remote-bash (`syntax error near '('`). Skriv til lokal fil (LF), `scp`, kør `ssh host "bash /sti.sh"`. Fuld regel: `10_PROJEKTER/CLAUDE.md`.

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver **xplat**) er den generiske, publicerede FOSS-app
afledt af Note10-rig'en: gør en gammel Android-telefon til et fjernstyret kamera + accessibility-
automations-motor + scrcpy/adb-bro over eget Tailscale-net, uden root. Udgives på **F-Droid**
(fdroiddata-MR !40810) + GitHub-releases (`hf1985/husk`) + `xplat.co/husk`. **Pure framework** (ingen
AndroidX, ingen deps) → nem F-Droid-build. 16 Java-kilder (2026-09-19) i `app/src/main/java/co/xplat/husk/`.

## Rolle i økosystemet (Husk er MOTOREN, de andre bygger ovenpå)
- **`P_add-on_phone-transport`** – delt transport (SSH+Termux+Tailscale + boot). Husk er uafhængig af
  den (ingen imports); transporten er hvordan man NÅR telefonen.
- **`husk-overbygning`** (privat, klon `$env:USERPROFILE\repos\husk-overbygning`) – tynd office-overbygning:
  ubemandet Discord-mødekamera. Bruger Husks 8127-RPC + kamera; indeholder INGEN motor-/kamera-/WD-logik.
- **`P_app_phone-devbox`** – 24/7 code-server + Claude Code i proot. Bruger Husks 8127 passivt.
- **`P_kontor`** – office-consumer (Medlyt/EPOS/SMTP/RB5009).

## Komponenter + porte (én proces)
- **8127** `RigAccessibilityService` – loopback-RPC (DexRPC-superset, linjebaseret): tap/swipe/find/click/
  state/gettext/dump/scroll/launch/global/text/enter/devoptions + in-process WD-recovery. Tom
  `onAccessibilityEvent` (henter noder on-demand).
- **8090** `ControlServer` – HTTP (0.0.0.0 + kilde-IP-ACL: loopback/RFC1918/Tailscale): kamera (`/snapshot`
  `/stream` `/set`), skærm (`/screen` `/screen.mp4` `/control` `/controlhw`), input-proxy til 8127,
  hardware (`/sensors` `/battery` …), mgmt (`/wd` `/pair` `/devoptions` `/flags`), motion (`/motion` `/events`).
  Hostes af CameraService/ScreenService (`Rig.ensureControlServer`), proces-singleton.
- **15557** `AdbForward` – app-native scrcpy/adb-bro over Tailscale (Termux-uafhængig).
- `BootReceiver` (boot + `MY_PACKAGE_REPLACED`), `CameraService`, `ScreenService`, `MainActivity`
  (UI), `Motion`/`Ntfy` (bevægelses-alarm). De to selv-opdaterings-klasser er slettet i 1.1.

## MUST-NOT-REGRESS-invarianter (LÆS docs/YDELSE-OG-DRIFT.md før du rører Screen/Camera/a11y)
Husk er idle det meste af tiden og må KUN bruge ressourcer on-demand. Fire invarianter (alle indført
efter konkrete CPU-/frys-regressioner – rul dem ALDRIG tilbage):
- **A – skærm-streaming er DOVEN:** `ScreenService.onFrame` koder kun JPEG når en `/screen`-klient ser
  med (`Rig.lastScreenClientMs`/`SCREEN_IDLE_MS`) el. motion er TIL. (Fix v0.9.19 – Discord-hak.)
- **B – a11y-masken er SMAL:** kun `typeWindowStateChanged` i `accessibilityconfig.xml`; udvides kun i
  5s efter en a11y-op via `keepCacheFresh()` (kaldt fra `onMain`, dækker BÅDE in-process OG shell-drevet
  `wd-up.sh` WD-recovery). Sæt ALDRIG `typeWindowContentChanged` permanent. (Fix v0.9.19.)
- **C – kameraet er DOVENT + Husk EVICTER aldrig:** `CameraService` åbner kun kamera-enheden når en
  forbruger (`/stream`/`/snapshot`/motion) bruger den OG kameraet er LEDIGT (`AvailabilityCallback`).
  Ingen forbruger → slip enheden. (Fix v0.9.21 – kamera-frys ved siden af Discord.)
- **D (FJERNET v0.9.22):** v0.9.21's display-0-bounce af `MainActivity` blev fjernet igen - den
  triggede Samsungs "genstart på anden skærm"-dialog + crashede scrcpy/Discord på DeX. Kamera-frysen
  dækkes af C alene. **Gen-indfør ALDRIG en cross-display launch/bounce** (Samsung-DeX-fjendtlig);
  Husk åbner hvor den launches og sameksisterer som vindue på DeX. Se docs/YDELSE-OG-DRIFT.md.

## Build, signering, release
Kanonisk build = Gradle `assembleRelease` i WSL (`~/android-build`, env21.sh = JDK21+SDK). **Se
`docs/BUILD.md`** (fuld procedure, G:→WSL-synk, signering, per-release-checkliste, F-Droid-CI-tjek).
Een-kommando: `gradle-build.sh`. **Byg IKKE via `/mnt/g`** (Drive i WSL flaky) – synk fra git-bash til
`//wsl.localhost/...` ELLER kald med `MSYS_NO_PATHCONV=1`. **Signeringsnøgle** (UDSKIFTET 2026-09-03: `CN=xplat, O=xplat, C=DK`, alias `husk`, RSA 4096,
SHA-256 `96195cfd…c17d`). Keystore OG adgangskode ligger i **vaulten** som login-item
`Husk release-signeringsnoegle (keystore husk-release.jks, base64)`; arbejdskopi i WSL
`~/android-build/husk-signing/husk-release.jks`. Adgangskoden står ALDRIG i en fil i repoet –
den gamle debug-nøgles kodeord gjorde, i et offentligt repo, og det var grunden til skiftet.
Se `docs/BUILD.md` §5. ALDRIG i repoet/Drive (`.gitignore` dækker `*.keystore` OG `*.jks` - sidstnævnte manglede indtil 2026-09-04, hvor den nye nøgle var ubeskyttet). Per release: følg **⛔ RELEASE-PLIGT-blokken
øverst i denne fil** (alle 7 trin, inkl. xplat.co-konstanter + DEPLOY + verifikation af begge
`latest.json`-endpoints); detaljer i `docs/BUILD.md` §6–7.
Nuværende: **1.1 / versionCode 52** (2026-09-18, bygget på `HFs_Dell`. Releasen lukker F-Droids
review-rapport af 15-09: den indbyggede updater (`Updater`/`InstallReceiver`, `REQUEST_INSTALL_PACKAGES`,
`/update`, `Rig.lastUpdate`, a11y-auto-tap af install-dialogen og de ti `update_*`-strenge) er FJERNET,
og den prebuilte `husk-latest.apk` er ude af kildetræet med `*.apk` i `.gitignore`. Nyt: `/set?front=0|1`
vælger kameraside over HTTP, og `/flags` bærer det valgte i feltet `front` – det er forudsætningen for
at PC-viewer'en kan blive et produkt uden ADB-kæden. Tagget sættes på PRÆCIS byggecommiten (fund 1:
`v1.0` sad på `02e069b`, mens `b75af7a` blev bygget).
Tidligere udgaver (0.9.25 → 1.0) med hvad hver enkelt aendrede og hvorfor: `docs/versionshistorik.md`. Den stod her indtil 2026-09-19 og blev flyttet fordi denne fil auto-loades i hver agents kontekst og kaeden var 2.147 bytes over loftet.

## Deploy til den KØRENDE rig (kamera-sameksistens) – se docs/YDELSE-OG-DRIFT.md §3
- `adb install -r <apk>` (når adb/WD er sund) → a11y/8127 re-binder selv (~4s), kameraet røres ikke;
  derefter `adb reboot` for ren fuld-tilstand (boot-kæden genrejser 8090/ScreenService + Discord-join).
- **Launch IKKE `MainActivity` på den kørende rig via `am start`** (selv som test): det forgrunder Husk
  nær DeX → Samsungs "restart on another display"-churn slår midlertidigt a11y fra (8127 nede). a11y er
  PONG efter hvert boot og stabil i fred; verificér kamera-fix via `/flags` (`camera:false`) +
  `dumpsys media.camera`-ejer i stedet. `settings put secure accessibility_enabled 1` gen-binder IKKE
  a11y live (kun ved næste reboot).

## Flåde + Termux-løs tailnet-transport – se docs/fleet-tailnet-transport.md
- **Husk er selv en tailnet-tjeneste:** 8090 (`ControlServer`) + 15557 (`AdbForward`) binder `0.0.0.0`
  bag kilde-IP-ACL (`Net.peerAllowed`: loopback/RFC1918/Tailscale) + valgfrit token. Enhver peer –
  også hfs-dell – styrer en enhed DIREKTE (`curl http://<ts-ip>:8090/…`, `/rpc?cmd=ping`→PONG a11y,
  `adb connect <ts-ip>:15557`) **uden Termux**. Termux var kun til overbygningens loopback-flader.
- **Flåde (ENSARTET 2026-09-07): alle tre på 1.0/51.** Efter en in-app-opdatering SKAL du efterprøve `/snapshot` OG `/screen.jpg` pr. enhed - porten kommer op uanset, så `/healthz` og `rpc ping` kan være grønne mens kamera- eller skærmtjenesten ligger nede (måleregel 422). Tidligere: 0.9.31/50 med den NYE nøgle (2026-09-04). Note10 blev geninstalleret først, de to spares bagefter. Målt efter geninstallationen på alle tre: a11y oppe, skærmdeling til, batteri-undtagelse på, og `/snapshot` svarer 200 med et rigtigt JPEG. Enheder: Note10 SM-N975U1 (A12, DeX, token, .103.102) + spare Sony **702SO**
  (A9, tokenløs, .101.101) + spare Samsung **SM-A102U1**/A10e (A11, tokenløs, .101.102).
- **Spares KAN itereres fuldt (KORRIGERET 2026-07-12 – »umuligt« var en skærm-slukket-fejldiagnose).**
  En idle spare SOVER skærmen → a11y ser kun navbar, gestus=`ERR cancelled`, home/back=no-op (blev
  fejltolket som »motoren død«; `/screen` komponerer panelet selv når det er slukket → narrede diagnosen).
  **Fix = `wake` FØRST.** Så virker fuld menneske-kontrol på begge: `wake`→`launch`→`tap`/`swipe`
  (lander på app)→`home/back`→`text`→`/screen.jpg`. Node-læsning pålidelig på A11, flaky på A9 – begge
  drives via **syn+koordinat-tap**. **Deploy PROVET headless:** `/update?force=1`→OS-install-dialog;
  Play Protect gater friske sideloads (»More details«→»Install without scanning«)→commit→8090 falder→
  **J4 self-healer** (ingen reboot). Harness: `pc/spare.ps1` / `pc/spare.sh`. Interaktivt:
  `http://<ts-ip>:8090/control`. Rest-gap: ren `adb reboot` kræver stadig éngangs-USB (IKKE nødvendig
  for iteration). Fuld analyse: `docs/fleet-tailnet-transport.md` §0/§4/§6/§7.

## Faste regler
- **Dansk** i docs/kommentarer/commits; danske gåseøjne »...«; brug ÆGTE æ/ø/å (ALDRIG aa/oe/ae) – men
  ASCII-ificér ALDRIG regex der matcher dansk system-UI (brug de rigtige tegn/wildcards der).
- **Deploy-politik:** grundigt testede ændringer (grøn build + verifikation) deployes/køres live straks;
  rul tilbage ved fejl. Destruktivt/irreversibelt bekræftes først.
- Gamle navne (`note10-app`, `com.khfrb.note10`, `dextap`, `DexRPC`, `meeting-camera`, `M650`) er renset
  – brug dem ikke. `CUTOVER-note10-engine-2026-06-18.md` er bevaret som historisk postmortem.

## Docs
`README.md` (overblik), `docs/BUILD.md` (build/release), `docs/YDELSE-OG-DRIFT.md` (ydelses-invarianter +
diagnostik + sikker rig-deploy), `docs/AUDIT-2026-06-21.md` + `CUTOVER-note10-engine-2026-06-18.md`
(historik), `fdroid/` (F-Droid-metadata), `pc/husk-companion.ps1` (scrcpy-companion),
`pc/spare.ps1` + `pc/spare.sh` (flåde-harness: wake+shot+launch+tap+swipe+update over 8090),
`pc/check-api-parity.sh` (release-gate: HUSK_API-katalog vs appens endpoints + versionCode).
