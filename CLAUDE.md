# P_app_husk – agent-kontekst

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
repoet/Drive (`.gitignore` dækker `*.keystore`). Per release: bump `app/build.gradle` (versionCode+Name)
+ opdatér `latest.json` + `husk-latest.apk` (signeret) + `fdroid/co.xplat.husk.yml` + tag `vX.Y.Z`.
Nuværende: **0.9.24 / versionCode 43** (0.9.24 = persistent token via `Settings.Global husk_token`, audit 2/7). Ingen GitHub Actions i repoet (Gradle-buildet er verifikationen).

## Deploy til den KØRENDE rig (kamera-sameksistens) – se docs/YDELSE-OG-DRIFT.md §3
- `adb install -r <apk>` (når adb/WD er sund) → a11y/8127 re-binder selv (~4s), kameraet røres ikke;
  derefter `adb reboot` for ren fuld-tilstand (boot-kæden genrejser 8090/ScreenService + Discord-join).
- **Launch IKKE `MainActivity` på den kørende rig via `am start`** (selv som test): det forgrunder Husk
  nær DeX → Samsungs "restart on another display"-churn slår midlertidigt a11y fra (8127 nede). a11y er
  PONG efter hvert boot og stabil i fred; verificér kamera-fix via `/flags` (`camera:false`) +
  `dumpsys media.camera`-ejer i stedet. `settings put secure accessibility_enabled 1` gen-binder IKKE
  a11y live (kun ved næste reboot).

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
(historik), `fdroid/` (F-Droid-metadata), `pc/husk-companion.ps1` (scrcpy-companion).
