# P_app_husk – agent-kontekst

> ## ⛔ RELEASE-PLIGT – en kodeændring er IKKE færdig før den er UDGIVET
>
> **ENHVER ændring af app-koden (`app/src/**`) SKAL udgives som en fuld, signeret release,
> FØR opgaven regnes som færdig.** Committet-men-ikke-udgivet = flåden (de fysiske telefoner)
> kører stadig den GAMLE kode; det er en UFÆRDIG opgave. Det gælder også autonome/uovervågede
> agenter (fx P_auto-optimering): stop ALDRIG ved commit. (Senest brændt: XSS-sikkerhedsfixet
> `e999ae4` blev committet men aldrig releaset – alle enheder kørte videre på 0.9.25 uden fixet.)
>
> **KRITISK fælde:** appens updater spørger `https://xplat.co/husk/latest.json` FØRST
> (GitHub-raw er kun fallback). Glemmes xplat.co-deployen, svarer enhederne »allerede nyeste«,
> selv om GitHub er opdateret (set 0.9.18–0.9.21). BEGGE endpoints SKAL vise den nye `versionCode`.
>
> **Release-huskeliste** (fuld procedure: `docs/BUILD.md` §4–7):
> 1. Bump `versionCode` + `versionName` – ÉT sted: `app/build.gradle`.
> 2. Byg `assembleRelease` i WSL + signér med den faste keystore (alias `ad`).
> 3. Opdatér repo'ets `latest.json` + `husk-latest.apk` (den signerede APK).
> 4. Opdatér `fdroid/co.xplat.husk.yml` (+ fork-metadata, MR !40810).
> 5. Opdatér HUSK-konstanterne (`HUSK_VERSION_*`) i `P_xplat/hosting/app.py` **OG deploy xplat.co**.
> 6. **API-DOK-GATE (obligatorisk):** ændrer releasen endpoints/params/respons/adgangsmodel? Ajourfør
>    `HUSK_API`-kataloget (+ OpenAPI-beskrivelsen) i `P_xplat/hosting/app.py` (driver BÅDE `/husk/api`
>    OG `/husk/openapi.json`), deploy xplat, og kør **`bash pc/check-api-parity.sh` → skal være GRØN**
>    (fejler ved udokumenterede/stale endpoints + versionCode-drift). Verificér `/husk/api` live.
> 7. Commit + tag `vX.Y.Z` + push; GitHub-release med den signerede APK.
> 8. Verificér: BÅDE `https://xplat.co/husk/latest.json` OG
>    `https://raw.githubusercontent.com/hf1985/husk/main/latest.json` viser den nye
>    `versionCode`, og F-Droid/GitLab-pipelinen er GRØN.
>
> **Definition af færdig:** begge `latest.json`-endpoints viser den nye version, F-Droid-pipelinen er
> grøn, **OG `pc/check-api-parity.sh` er grøn + `/husk/api` er ajour** (trin 6). Før ALT dette er
> opgaven ÅBEN – uanset hvor grøn builden er lokalt. (Erfaring 2026-07-12: en release bumpede versionen
> men glemte 5 nye endpoints + CSRF-modellen i `/husk/api`; gaten fanger netop dét.)
> (Flåden opdateres derefter via in-app Updater – aldrig on-phone build/adb install.)

> **Miljø-regel (Windows/PowerShell→ssh):** sender du en `ssh`/`scp`/`mysql -e`-kommando med `(` `)` `$()` backtick, linjeskift eller `"`? Inline den IKKE – PowerShell spiser embedded quotes, så metakarakterer brækker remote-bash (`syntax error near '('`). Skriv til lokal fil (LF), `scp`, kør `ssh host "bash /sti.sh"`. Fuld regel: `10_PROJEKTER/CLAUDE.md`.

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver **xplat**) er den generiske, publicerede FOSS-app
afledt af Note10-rig'en: gør en gammel Android-telefon til et fjernstyret kamera + accessibility-
automations-motor + scrcpy/adb-bro over eget Tailscale-net, uden root. Udgives på **F-Droid**
(fdroiddata-MR !40810) + GitHub-releases (`hf1985/husk`) + `xplat.co/husk`. **Pure framework** (ingen
AndroidX, ingen deps) → nem F-Droid-build. 18 Java-kilder i `app/src/main/java/co/xplat/husk/`.

## Rolle i økosystemet (Husk er MOTOREN, de andre bygger ovenpå)
- **`P_add-on_phone-transport`** – delt transport (SSH+Termux+Tailscale + boot). Husk er uafhængig af
  den (ingen imports); transporten er hvordan man NÅR telefonen.
- **`husk-overbygning`** (privat, klon `C:\Users\hf198\repos\husk-overbygning`) – tynd office-overbygning:
  ubemandet Discord-mødekamera. Bruger Husks 8127-RPC + kamera; indeholder INGEN motor-/kamera-/WD-logik.
- **`P_app_phone-devbox`** – 24/7 code-server + Claude Code i proot. Bruger Husks 8127 passivt.
- **`P_kontor`** – office-consumer (Medlyt/EPOS/SMTP/RB5009).

## Komponenter + porte (én proces)
- **8127** `RigAccessibilityService` – loopback-RPC (DexRPC-superset, linjebaseret): tap/swipe/find/click/
  state/gettext/dump/scroll/launch/global/text/enter/devoptions + in-process WD-recovery. Tom
  `onAccessibilityEvent` (henter noder on-demand).
- **8090** `ControlServer` – HTTP (0.0.0.0 + kilde-IP-ACL: loopback/RFC1918/Tailscale): kamera (`/snapshot`
  `/stream` `/set`), skærm (`/screen` `/screen.mp4` `/control` `/controlhw`), input-proxy til 8127,
  hardware (`/sensors` `/battery` …), mgmt (`/wd` `/pair` `/update` `/flags`), motion (`/motion` `/events`).
  Hostes af CameraService/ScreenService (`Rig.ensureControlServer`), proces-singleton.
- **15557** `AdbForward` – app-native scrcpy/adb-bro over Tailscale (Termux-uafhængig).
- `BootReceiver` (boot), `CameraService`, `ScreenService`, `MainActivity` (UI), `Motion`/`Ntfy`
  (bevægelses-alarm), `Updater`/`InstallReceiver` (in-app self-update).

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
`//wsl.localhost/...` ELLER kald med `MSYS_NO_PATHCONV=1`. **Signeringsnøgle** (CN=Debug, O=KHFRB, C=DK,
alias `ad`, pass `android`) ligger i WSL `~/android-build/husk-signing/` + telefon-backup – ALDRIG i
repoet/Drive (`.gitignore` dækker `*.keystore`). Per release: følg **⛔ RELEASE-PLIGT-blokken
øverst i denne fil** (alle 7 trin, inkl. xplat.co-konstanter + DEPLOY + verifikation af begge
`latest.json`-endpoints); detaljer i `docs/BUILD.md` §6–7.
Nuværende: **0.9.29 / versionCode 48** (audit-runde 2 via Note10, log `docs/AUDIT-2026-07-12-runde2.md`: selv-review fangede en HIGH-regression jeg indførte i 0.9.28 – `acceptInstallConsent` læste stale `lastUpdate` → gentaget `/update` efter »latest« stallede; rettet m. synkron »checking«-reset + `sawProgress`-gate. Plus 3 LOW: /vibrate-loft, sensor-NaN-guard, InstallReceiver-fejl-synlighed. Note10-rig live-verificeret sund på 0.9.28: kamera/H.264/hardware/DeX/CSRF). Tidligere: 0.9.28/47 (stor sikkerheds+korrektheds+ydelses-audit 2026-07-12 – 3 parallelle review-agenter + manuel verifikation; beslutnings-log i `docs/AUDIT-2026-07-12.md`. Højdepunkter: CSRF/DNS-rebinding-forsvar i ControlServer; kamera-permanent-død + H.264-ANR + PackageInstaller-session-læk fikset; A14-sikker specialUse→camera-FGS selv-heal (uændret på ≤A13); motion-på-skærm-CPU-spild fjernet + Bitmap/BAOS genbrug. Ingen invariant A-D svækket). Tidligere: 0.9.27/46 (`acceptInstallConsent` lærte Play Protects »Install without scanning«-sti; on-device auto-accept KUN delvist pålidelig på spares pga. flaky a11y-`getWindows()` → pålidelig ubemandet self-update = Play Protect-scanning FRA ELLER PC-harness vision+tap; docs/fleet-tailnet-transport.md §7). Tidligere: 0.9.26/45 (reflekteret-XSS-fix); 0.9.25/44 (J4: `BootReceiver` håndterer `MY_PACKAGE_REPLACED` → 8090 rejser sig efter in-app-opdatering – bevist virksom på spares 2026-07-12). Ingen GitHub Actions i repoet (Gradle-buildet er verifikationen).

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
- **Flåde (alle 0.9.26/45):** Note10 SM-N975U1 (A12, DeX, token, .103.102) + spare Sony **702SO**
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
