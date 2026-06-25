<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Ydelse, ressource-invarianter og drift

Denne fil samler det en agent/udvikler skal vide for at **drifte og ændre Husk uden at
genintroducere CPU-regressioner** - især når Husk kører 24/7 på en enhed der DELER ressourcer
(CPU/kamera) med en anden app (fx Discord-mødekameraet, jf. `husk-overbygning`).

Læs også [`BUILD.md`](BUILD.md) (byg/signér/release) og `AUDIT-2026-06-21.md`.

---

## 1. Ressource-invarianter (MÅ IKKE rulles tilbage)

Husk er en server der mest af tiden er **idle**. Den må kun bruge CPU når noget faktisk
efterspørger arbejde. To dyrt-lærte invarianter håndhæver det. Begge blev brudt af regressioner
(v0.9.14/v0.9.15) der fik en co-resident Discord-videostream til at **hakke**, fordi Husk åd
~40 % CPU i tomgang (postmortem i §4).

### Invariant A - skærm-streaming er DOVEN (kun når en klient ser med)
`ScreenService` fanger skærmen til en `VirtualDisplay`/`ImageReader`. Selve det at *kode* hver
frame (Bitmap-alloc + `copyPixelsFromBuffer` + JPEG-compress i `onFrame`) er DYRT og kører på
tråden `screen-bg`. 

- **Regel:** `onFrame` må KUN lave det dyre arbejde når en `/screen`(-snapshot)-klient har bedt om
  en frame inden for `Rig.SCREEN_IDLE_MS` (4 s), ELLER `Rig.motionEnabled` er sat. Ellers
  `return` med det samme (frame droppes billigt; `VirtualDisplay` spejler videre via compositor).
- **Mekanik:** `ControlServer` stempler `Rig.lastScreenClientMs = SystemClock.uptimeMillis()` i
  `writeScreenStream`-loopet (hver iteration) og i `writeScreenSnapshot`. `onFrame` læser det.
- **Hvorfor det er sikkert:** `/screen.mp4` (H.264) bruger IKKE JPEG-vejen - den har sin egen
  lazy `Rig.ensureH264()`/`stopH264()`. MJPEG-`/screen`-loopet poller hver 20 ms og gen-stempler,
  så frames flyder inden for én throttle-periode (~60 ms) efter en klient forbinder.
- **ADVARSEL:** Gør IKKE skærm-capture "altid-på" igen "for at /control skal være klar med det
  samme". Det koster ~15-46 % CPU 24/7 uden forbruger. Klar-på-få-ms-når-en-klient-forbinder er
  godt nok.

### Invariant B - accessibility-event-masken er SMAL i steady-state
`RigAccessibilityService` har en TOM `onAccessibilityEvent`. Den henter selv noder on-demand
(`getWindowsOnAllDisplays()`/`getRoot()`) når en RPC kommer; den er IKKE afhængig af at modtage
events. Et abonnement på den højfrekvente `typeWindowContentChanged` tvinger derfor frameworket
til at beregne + udsende events fra ENHVER app (fx Discords video-UI: hundredvis/sek) til Husks
proces - og fylde `AccessibilityCache` i Husks adresserum (millioner af node-allokeringer/min) -
**til ingen nytte**.

- **Regel:** `accessibilityconfig.xml` abonnerer KUN på `typeWindowStateChanged` i steady-state.
- **WD-recovery-undtagelsen:** Recovery/dev-options SCROLLER i Indstillinger, og uden friske
  content-events invaliderer cachen ikke → en `state`/`gettext` efter en `scroll` læser FØR-scroll-
  skærmen og scroller forbi (Android 12/API 31 har ingen `clearCache()`). Derfor UDVIDER vi masken
  midlertidigt: `keepCacheFresh()` (kaldt fra `onMain`) sætter `typeWindowContentChanged|WINDOWS_CHANGED`
  til i 5 s efter enhver a11y-operation, og snævrer ind igen (debounced).
- **Hvorfor `onMain` er det rigtige sted:** BÅDE in-process WD-recovery (`recoverWirelessDebugging`
  → `stateD`/`scrollD`) OG **shell-drevet `wd-up.sh`** (RPC `scroll`/`state`/`gettext` over 8127)
  funneler gennem `onMain`. Wrap derfor IKKE bare recovery-metoderne - så dækker du kun
  in-process-stien og boot-WD-recovery (shell) går i stykker.
- **ADVARSEL:** Sæt IKKE `typeWindowContentChanged` (eller `typeAllMask`) permanent i
  `accessibilityconfig.xml` igen.

### Invariant C - kameraet holdes DOVENT og Husk EVICTER aldrig en anden app
Husk er universel, men på en rig der DELER kameraet med en anden app (Discord-mødekameraet) må Husk
aldrig rive kameraet til sig. To regler i `CameraService`:

- **Doven:** kamera-ENHEDEN åbnes kun når noget faktisk forbruger Husks feed (`/stream` eller
  `/snapshot` inden for `Rig.CAMERA_IDLE_MS`) ELLER `motionEnabled`. Ingen forbruger → enheden
  slippes (`closeCameraDevice`), så en anden app kan bruge den. `ControlServer` stempler
  `Rig.lastCameraClientMs`; en `demandCheck` (camHandler, hvert 1 s) åbner/lukker efter
  efterspørgsel. (Samme princip som Invariant A.)
- **Aldrig eviction:** `openCamera` åbner KUN når kameraet er LEDIGT - en
  `CameraManager.AvailabilityCallback` sætter `othersHaveCamera` når en anden app holder det, og så
  springer `openCamera` over (Android ville ellers give en forgrunds-Husk kamera-PRIORITET og
  smide den anden app af). Ved `onDisconnected/onError` (taget af en anden) genåbner Husk ALDRIG af
  sig selv; availability-callback'en rydder flaget når kameraet bliver ledigt igen.
- **Hvorfor:** før dette gjaldt, åbnede Husks `CameraService` kameraet uafhængigt, og når Husks
  `MainActivity` kom i FORGRUNDEN (bruger åbnede appen) fik Husk kamera-prioritet og **evictede
  Discord** → mødets video frøs. Det var roden til "kameraet fryser når jeg åbner/lukker Husk".
- **ADVARSEL:** Lad IKKE `CameraService` åbne kameraet eagerly (fx i `onStartCommand` eller en blind
  reopen-watchdog der genåbner ved `onError`). Det evicter co-resident apps.

### (Tidligere Invariant D - display-0-bounce: FJERNET i v0.9.22, gør IKKE igen)
v0.9.21 lod `MainActivity.onCreate` "bounce" til display 0 hvis den blev åbnet på en anden skærm (DeX),
for at undgå at fortrænge mødet. Det var en FEJL: bouncen tvinger et skift på tværs af skærme, hvilket
udløser Samsungs **"App kører på en anden skærm - genstart?"**-dialog OG får scrcpy (der spejler DeX) +
Discord til at crashe ved skærm-churnet. Kamera-frysen håndteres allerede af **Invariant C alene**
(Husk evicter ikke kameraet), så bouncen gav kun skade. **v0.9.22 fjernede den** → Husk åbner nu hvor
den launches; på DeX åbner den som et almindeligt vindue (resizeableActivity er default-true for
targetSdk 34) der sameksisterer med Discord uden at fortrænge det. **Gen-indfør ALDRIG en cross-display
launch/bounce af `MainActivity`** - det er Samsung-DeX-fjendtligt. (Hvis Husks UI ikke ønskes på
mødeskærmen: åbn den på telefonskærmen eller brug browser-`/control`.)

---

## 2. Diagnostik på en kørende host (find CPU-tyven)

**Termux `top` lyver om systemet.** På non-root Android ser en Termux-`top` (og `/proc/loadavg`
inde i proot) kun Husks egne ~12 proc, fordi Android skjuler andre UID'ers `/proc` (hidepid). En
Termux-`top` der viser "0 % CPU" mens enheden hakker er normalt - kig systembredt via adb i stedet:

```bash
# systembredt + per-proces + PAGE FAULTS (thrash-fingeraftryk):
adb shell "dumpsys cpuinfo | head -40"
# per-TRÅD i én proces (finder hvilken tråd der brænder - fx screen-bg vs cam-bg):
HPID=$(adb shell pidof co.xplat.husk | tr -d '\r')
adb shell "top -b -n1 -H -p $HPID"
# hvem ejer kameraet + eviction-historik:
adb shell "dumpsys media.camera | grep -iE 'Client Package Name|EVICT'"
# Husks egen tilstand (motor/kamera/skærm/motion):
curl -s http://127.0.0.1:8090/flags
```

**Fortolkning (lært i v0.9.19-sagen):**
- `screen-bg` med høj akkumuleret `TIME+` = Invariant A brudt (skærm-capture uden forbruger).
- Millioner af `minor faults` på `co.xplat.husk` i et kort vindue = Invariant B brudt (a11y-event-
  flod) ELLER tung frame-kodning.
- `co.xplat.husk` skal være ~0 % CPU i tomgang. Er den ikke det, er en af invarianterne brudt.

---

## 3. Sikker deploy til en KØRENDE rig (kamera-sameksistens)

På en rig hvor Husk deler enheden med en anden kamera-app (Discord), gælder:

- **`adb install -r <apk>` er sikkert** når adb er sundt (WD oppe): processen dræbes, a11y/8127
  re-binder af sig selv (~4 s), kameraet røres ikke. Verificér bagefter med `8127 PONG`.
- **REBOOT for ren fuld-tilstand:** `adb install -r` genstarter IKKE `ScreenService`/`ControlServer`
  (8090) - dem starter `CameraService.onCreate` (`Rig.ensureControlServer()` + `maybeAutoScreenShare`).
  Et `adb reboot` lader boot-kæden (på rig'en: `husk-overbygning/boot-start.sh` → `wd-up.sh` →
  `join.sh`) genrejse alt rent.
- **LAUNCH IKKE `MainActivity` PÅ EN KØRENDE RIG via `am start` (til test).** `MainActivity` er den
  eneste eksporterede aktivitet (`ScreenConsentActivity`/services er `exported=false`). Kamera-delen
  er nu beskyttet (Invariant C/D: Husk evicter ikke + UI bouncer til display 0), MEN at forgrunde
  `MainActivity` nær DeX kan stadig trigge Samsungs "App kører på en anden skærm - genstart?" →
  proces-churn der midlertidigt afbinder a11y (8127 nede; `am start --display 2` rapporterer ofte
  "Activity kept for the user" men churner alligevel). **Test derfor kamera-fixet på andre måder**
  (verificér `camera:false` i `/flags` + `dumpsys media.camera`-ejer forbliver den anden app, INGEN
  `Evicted by ... co.xplat.husk`), og **genrejs 8090/ScreenService med en reboot, ikke en app-launch.**
  a11y er PONG lige efter boot og forbliver stabil når rig'en får fred - det er aktive
  DeX-forgrunds-launches der churner den.
- I tomgang er den korrekte sameksistens-tilstand: Husks `CameraService` baggrunds-`openCamera`
  bliver AFVIST mens den anden app (forgrund) holder kameraet (`camera:false` i `/flags`) - det er
  rigtigt, ikke en fejl.

Byg/signér: se [`BUILD.md`](BUILD.md). Kort: i WSL `MSYS_NO_PATHCONV=1 wsl.exe --cd '~' -- bash -lc
'HUSK_KEYSTORE=$HOME/android-build/husk-signing/debug.keystore bash ".../gradle-build.sh"'`, så
`scp` APK'en til telefonen og `adb install -r`.

---

## 4. Postmortem: "Discord hakker efter de seneste ændringer" (v0.9.19, 2026-06-25)

**Symptom:** Discord-mødekameraet hakkede; tidspunkt ukendt. **Diagnose:** `dumpsys cpuinfo` viste
`co.xplat.husk` på 40 % CPU i et møde uden self-check (burde være ~0). Per-tråd: `screen-bg`
havde brugt 9,2 timers CPU over 2,5 døgns oppetid (~46 % vedvarende), og der var NUL klienter på
8090. Årsag = to regressioner fra 22. juni:

1. **v0.9.14** gjorde reelt skærm-capture til en altid-på 16 fps JPEG-kodning (Invariant A brudt).
   Den dominerende post.
2. **v0.9.15** tilføjede `typeWindowContentChanged` til a11y-masken permanent (Invariant B brudt) -
   Discords video-UI fyrede uafbrudt indholds-events → ~9,8 mio. minor faults/5 min i Husk-procen.

Begge sultede Discord+kamera (kørte sultne på 66 %/48 % → hak). **Fix (v0.9.19):** Invariant A +
B som beskrevet i §1. **Målt efter:** `screen-bg` 46 %→0 % i tomgang (steg kun mens en /screen-
klient så med); husk-CPU 40 %→0 %; Discord 66 %→83 % + kamera 48 %→76 % (den frigjorte CPU gik til
mødet → glat). Boot-WD-recovery (shell-drevet `wd-up.sh`) virkede stadig = `keepCacheFresh` dækker
shell-stien. **Lærdom:** "altid-klar" features (skærm-capture, brede a11y-events) er gratis at
designe men dyre at køre 24/7 ved siden af en anden tung app - gør dem efterspørgsels-drevne.
