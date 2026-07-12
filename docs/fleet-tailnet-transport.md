# Husk-flåde + Termux-løs tailnet-transport

Dato: 2026-07-02. Kanonisk notat for hvordan Husk-enheder nås og styres **uden Termux**, plus
flåde-inventar og en ærlig reboot-gap-analyse for spare-enhederne. Pointer-memory:
`note10-meeting-camera`.

## 0. STATUS (KORREKTION 2026-07-12) – spares KAN itereres fuldt; »umuligt« var en fejldiagnose

> **⚠️ Den 2026-07-02-konklusion længere nede (»der findes INGEN headless vej … a11y kan
> HVERKEN læse el. injicere«) er FORKERT og er bevaret nedenfor kun som historik.** Rodårsagen
> var triviel: **en idle, ladende spare SOVER skærmen.** Med slukket panel returnerer a11y kun
> navbar-vinduet, `dispatchGesture` svarer `ERR cancelled`, og `global home/back` er no-ops –
> præcis de tre symptomer der blev tolket som »motoren er død«. `/screen` (MediaProjection)
> komponerer det logiske display selv når panelet fysisk er slukket, så et skarpt launcher-billede
> narrede diagnosen til at tro enheden var vågen + oplåst. **Løsningen er ét ord: `wake` FØRST.**

**Live-verificeret 2026-07-12 på BEGGE spares (Sony 702SO/A9 .101 og Samsung A10e/A11 .102), 0.9.26/45:**

1. **Fuld menneske-ækvivalent kontrol virker på begge, når skærmen er vækket:** `wake` →
   `launch` (åbn enhver app/intent) → `tap`/`swipe` (lander på app-vinduet; bevist ved at scrolle
   Indstillinger) → `global home/back/recents` → `text` (tastatur) → `/screen.jpg` (syn). Alt
   returnerer OK og gør synligt det rigtige (skærmbilleder gemt under bevis-runden).
2. **a11y-node-læsning:** pålidelig på .102 (A11); **flaky på .101** (A9's `getWindows()`-vej –
   finder nogle app-noder, misser andre i samme kald). Begge kan drives uanset via **syn + koordinat-tap**
   (`/screen.jpg` som øjne, læs koordinater, `tap x y`). På system-DIALOGER (PackageInstaller/Play
   Protect) er `find` upålidelig (substring rammer forkert knap) → brug altid koordinat-tap.
3. **Deploy-benet er PROVET headless på .102:** `/update?force=1` henter + commit'er sessionen;
   **Google Play Protect** gater friske sideloads (»App scan recommended« – kun *Scan app* / *Don't
   install app*, men »More details« afslører **»Install without scanning«**). Tap den → installen
   commit'er → app-processen dør (8090 falder kort) → **J4 (`MY_PACKAGE_REPLACED`) rejser
   ControlServer igen på få sekunder** (self-heal, INGEN reboot). `lastUpdate` nulstilt til "" =
   frisk proces = bevist reinstall.
4. **Vin-kanalen** (`/screen.jpg`) skal bruge et `wake` hvis skærmen sov (ellers tom/50-byte JPEG).

**Iterér i praksis:** brug harnessen `pc/spare.ps1` (Windows) / `pc/spare.sh` (Git Bash/WSL) –
verber `shot|wake|launch|tap|swipe|home|back|find|text|update|control|health`. Eller åbn bare
`http://<ts-ip>:8090/control` i en browser for en live skærm med klik/tastatur. Se §7.

**Rest-gap (IKKE nødvendig for iteration):** en ren ubemandet `adb reboot` kræver stadig adb, som
ikke kan bootstrappes headless (se §4). MEN iteration behøver ikke reboot (J4 self-healer efter
opdatering). En *seende* reboot via QS-strømmenu (træk QS ned → tap ⏻ → tap »Genstart«, alt synligt
via `/screen` + tapbart via gestus nu hvor skærmen vækkes) er blevet plausibel, men er endnu utestet.

**Fleeten er robust:** begge spares selvhelbreder efter enhver reboot (`BootReceiver` + a11y-rebind).

## 1. Nøgle-indsigt: Husk er selv en tailnet-tjeneste (Termux er IKKE nødvendig for Husk)

`ControlServer` (**8090**, HTTP) og `AdbForward` (**15557**, adb/scrcpy-bro) binder begge til
`0.0.0.0` – ikke kun loopback. De beskyttes af en delt **kilde-IP-ACL** (`Net.peerAllowed`:
loopback + RFC1918 + Tailscale-CGNAT 100.64/10) plus et **valgfrit token** (`Rig.token` /
`Settings.Global husk_token`; tomt token → kun IP-ACL'en beskytter).

hfs-dell er selv en peer på samme tailnet. Derfor kan PC'en nå enhver Husk-enhed **direkte**:

```
curl -s http://<tailscale-ip>:8090/healthz          # 200
curl -s http://<tailscale-ip>:8090/flags            # health-JSON
curl -s "http://<tailscale-ip>:8090/rpc?cmd=ping"   # a11y (8127) via passthrough -> PONG
adb connect <tailscale-ip>:15557                     # adb-bro (kræver WD-port cachet, se §4)
```

Termux blev kun brugt til overbygningens **loopback-only** flader (8127-RPC + shell-drevet
`wd-up.sh`/adb) på Note10-rig'en. Selve Husk-motoren har aldrig krævet Termux for at blive nået
fra en anden tailnet-peer. Det betyder de to spare-telefoner (uden Termux) styres fuldt via 8090.

### 8090-endpoints (alle token-gated undtagen `/healthz` og `/`)
`/flags` (health), `/info` (app+device+net+battery), `/battery`, `/sensors`, `/display`,
`/connectivity`, `/wd` (trigger WD-recovery → returnerer adb ip:port; kræver a11y), `/pair`,
`/update` (in-app self-update; kræver a11y), `/rpc?cmd=<8127-kommando>` (generisk a11y-passthrough:
ping/find/click/dump/tap/launch/state/gettext/global/wake/…), samt kamera/skærm-streams.

## 2. Flåde-inventar (alle på Husk 0.9.25 / versionCode 44, 2026-07-02)

| Rolle | Tailscale-IP | Model | Android | DeX | Token | Transport |
|---|---|---|---|---|---|---|
| Kontor-mødekamera (rig) | 100.100.103.102 | Samsung SM-N975U1 (Note10+) | 12 | ja | håndhævet | Termux+SSH (8022) **og** direkte 8090/15557 |
| Spare A | 100.100.101.101 | Sony **702SO** (Xperia XZ1 Compact) | **9** (SDK 28) | nej | ingen | kun direkte 8090/15557 (ingen Termux) |
| Spare B | 100.100.101.102 | Samsung **SM-A102U1** (Galaxy A10e) | **11** (SDK 30) | nej | ingen | kun direkte 8090/15557 (ingen Termux) |

Note10 = SM-N975U1; forveksl ALDRIG med den personlige S25 (SM-S938B). Spare A/B er tokenløse
(verificeret: `/flags` + `/info` svarede 200 uden token) → ingen hemmelighed nødvendig for at nå dem.

## 3. Bevist Termux-løs cyklus på begge spares (health + kontrol)

Kørt 2026-07-02 mod .101 og .102, alt grønt på begge:
`/healthz` 200 · `/flags` (`a11y:true, batteryOptIgnored:true, camera:false`) · `/info`
(model/Android/net/batteri) · `/battery` · sensorer · **`/rpc?cmd=ping` → PONG** (a11y-motoren
svarer via 8090-passthrough, uden Termux, uden loopback-adgang). Begge lader, batteri 100 %.

Dvs. hele **health- og kontrol-benet** af cyklussen virker identisk på to andre modeller og
Android-versioner. Det eneste ben der IKKE kunne lukkes headless er **reboot** (§4).

## 4. Reboot-gap: hvorfor en sikker, ubemandet genstart ikke kunne trigges på spares (endnu)

> **RETTELSE 2026-07-12 (se §0):** delkonklusionen i pkt. 1 nedenfor om at »a11y-motoren på .102
> HVERKEN kan læse ELLER injicere UI« er FORKERT – skærmen var slukket under testen. Med `wake`
> først læser og injicerer .102 fint (og .101 injicerer fint; node-læsning er flaky på A9). Selve
> adb-bootstrap-gappet (adb kan ikke etableres headless uden éngangs-USB) står stadig – men det er
> IKKE nødvendigt for at iterere (J4 self-healer efter self-update uden reboot). En SEENDE reboot via
> QS-strømmenuen er nu plausibel (skærmen kan vækkes + gestus lander), men utestet.

En reboot-selvhelbredelses-test (som på Note10) kræver en trigger. Alle veje for en **non-root**
Android blev gennemgået:

1. **`adb reboot`** – kræver en adb-forbindelse. Headless-etablering fejler på begge:
   - **Wireless Debugging (Android 11+, .102):** både `recoverWirelessDebugging()` og
     `startWdPairing()` skal DRIVE Indstillings-UI'et via a11y (åbne udviklerindstillinger, rulle til
     WD-rækken, klikke, læse ip:port + evt. 6-cifret pairing-kode). **Men a11y-motoren på .102 kan
     hverken LÆSE eller INJICERE UI** (verificeret 2/7 via `/screen`, se §6): node-læsning returnerer
     KUN SystemUI-navbaren (`Recents`) på enhver skærm, og HVER injiceret gestus (`tap`/`swipe`) svarer
     `ERR cancelled` (dispatchGesture onCancelled), mens `global home`/`back` er no-ops. Skærmen stod
     LÅST OP (profil-avatar + »Devices/Media« synlige, altså INGEN keyguard) – det er a11y-motoren
     selv, ikke en sikret skærm, der er blokeret. Da UI'et ikke kan drives, kan WD hverken toggles
     eller pares → adb kan ikke bootstrappes, **uanset om WD-toggle allerede er slået til**.
     (Rettelse: tidligere »adbd binder 127.0.0.1-only« var en over-konklusion; den faktiske blokering
     ligger opstrøms – UI'et kan slet ikke drives – så adbd's bind er irrelevant her.)
   - **.101 (Android 9):** Wireless-Debugging-subsystemet findes slet ikke. → blokeret.
   - **Klassisk `adb tcpip 5555`** (adbd rebinder `0.0.0.0:5555`, direkte nåelig over Tailscale,
     ingen bro) – men at slå tcpip-tilstand til kræver i sig selv en allerede-autoriseret adb-session
     (eller root). Hønen-og-ægget uden en første fysisk USB-autorisation. → blokeret headless.
2. **a11y `GLOBAL_ACTION_POWER_DIALOG`** – IKKE wired i Husks `doGlobal` (kun
   back/home/recents/notifications/quicksettings). Selv hvis tilføjet: power-menuen er en
   SystemUI-**overlay** usynlig for `getRootInActiveWindow` → kan ikke ses/klikkes sikkert.
3. **Launch en reboot-Intent** – Android har ingen offentlig reboot-Intent for ikke-system-apps
   (`REBOOT` = signature/system-permission). → umuligt for en non-root app.
4. **Koordinat-tap i power-menuen (One UI 3 lægger ⏻ i QS-headeren)** – `/screen` (§6) gør et tap
   SET (ikke blindt): man kan se »Genstart« før man rammer. MEN på .102 er dette alligevel dødt,
   fordi a11y-gestus-injektion returnerer `ERR cancelled` (samme blokering som pkt. 1) – tap'et lander
   aldrig. Et ægte BLINDT power-tap (uden `/screen`-verifikation, fx på en enhed hvor injektion
   virkede) forbliver AFVIST: risiko for utilsigtet sluk uden fysisk gendannelse. Gøres aldrig.

**Konklusion:** en ren, sikker, ubemandet reboot af .101/.102 kræver adb, og adb kan ikke etableres
headless på dem i nuværende tilstand. Ingen state-ændrende handling er udført på spares.

### Éngangs-oplåsning (fysisk, pr. enhed)
Slut spare til en PC via USB én gang → `adb devices` og godkend RSA-nøglen (»tillad altid«) →
`adb tcpip 5555` (adbd på `0.0.0.0:5555`, direkte over Tailscale) ELLER slå Wireless Debugging til +
`adb pair`. Derefter virker `adb reboot` headless, og hele cyklussen kan lukkes som på Note10.

### Er reboot-benet overhovedet nødvendigt for spares?
Spares kører intet DeX-kamera og har ingen overbygnings-vagthunde. Selvhelbredelse efter ENHVER
manuel/strøm-reboot hviler på Husks egen `BootReceiver` (`BOOT_COMPLETED`; begge ≤ Android 11 → ingen
FGS-start-blok) + a11y-auto-rebind (a11y-indstillinger overlever reboot) + `batteryOptIgnored:true`.
Det kan først PROVES ved en faktisk reboot – hvilket er præcis det ben der er blokeret headless.
Rimeligt valg: hold spares som health/kontrol-noder, og lav éngangs-oplåsningen når en af dem
alligevel er fysisk ved hånden.

## 5. J4 (self-update-selvhelbredelse) – bekræftet, IKKE kun fremadrettet

0.9.25's `BootReceiver` håndterer `ACTION_MY_PACKAGE_REPLACED` (FGS-start-undtaget, samme klasse som
`BOOT_COMPLETED`). Denne broadcast leveres til den **netop-installerede** pakkes receiver – altså
0.9.25's egen kode. Derfor virkede selvhelbredelsen allerede for **selve 0.9.24→0.9.25-opdateringen**:
8090 rejste sig efter in-app-opdateringen uden reboot/QUICKBOOT-nudge. Formuleringen »gevinsten er
fremadrettet / opdateringen FRA 0.9.24 bruger den gamle recovery én sidste gang« er derfor upræcis og
er rettet i rel-notes, audit-notatet og memory.

## 6. Ny indsigt (2/7): `/screen` er en SYNS-kanal uafhængig af a11y

`/screen` (MediaProjection MJPEG, `multipart/x-mixed-replace; boundary=rigscreen`) leverer et LIVE
billede af telefonens skærm **selv når a11y-node-læsning er død**. På .102 – hvor `dump/state/gettext`
kun ser navbaren – gav `/screen` et fuldt, skarpt skærmbillede (launcher, QS-shade, ur der tikkede),
og jeg kunne læse alt visuelt. Det er to helt adskilte rør: MediaProjection (pixels) vs.
`getRootInActiveWindow` (a11y-noder). Praktisk værdi:
- **Fjern-syn af enhver Husk-enhed** uden Termux/adb: hent `/screen`, skær første JPEG ud
  (`FFD8…FFD9`), læs med vision. Nyttigt til diagnostik af rig'en (»hvad står der egentlig på
  skærmen?«) når a11y svigter.
- **~~Men syn ≠ kontrol.~~ RETTET 2026-07-12:** påstanden om at `tap/swipe`=`ERR cancelled` og
  `home/back`=no-op på .102 var en **skærm-slukket-artefakt**. `/screen` viser et skarpt billede
  fordi MediaProjection komponerer det logiske display selv med fysisk slukket panel – hvilket narrede
  diagnosen. Med `wake` FØRST HANDLER a11y fint (syn OG kontrol). Se §0.
- **Nemmere end at carve MJPEG:** brug `GET /screen.jpg` (ét enkelt JPEG-snapshot). Carve-snippet fra
  `/screen`-MJPEG'en er stadig gyldig (`b'\xff\xd8'`..`b'\xff\xd9'`), men `/screen.jpg` er én curl.

**Bivirkning observeret 2/7:** under forsøgene endte .102's skærm på en fastlåst QS-shade (mine
gestus kunne ikke lukke den igen, da injektion cancelleres). Harmløst på en ladende spare – nulstilles
ved næste fysiske berøring / skærm-timeout. Ingen state-ændring udført.

## 7. Iterations-workflow (harness) – hvordan man i praksis arbejder på en spare

Kernen: **væk skærmen, SE den, HANDL på koordinater.** En idle spare sover; alt kontrol-arbejde
starter med et `wake`. Harnessen `pc/spare.ps1` (Windows/PowerShell) og `pc/spare.sh` (Git Bash/WSL)
pakker 8090-fladen ind. Aliaser: `a9`/`sony` = .101, `a11`/`samsung` = .102, `note10`/`rig` = .103.102
(sidstnævnte kræver token i `$env:HUSK_TOKEN`; spares er tokenløse).

```
spare.ps1 a11 health                          # /healthz + /flags + /info
spare.ps1 a11 shot                            # vaek + gem + aabn skaermbillede (dine oejne)
spare.ps1 a11 launch android.settings.SETTINGS
spare.ps1 a11 tap 360 960                      # laes koordinater FRA skaermbilledet
spare.ps1 a11 swipe 360 1000 360 500 300       # scroll
spare.ps1 a11 home | back | recents
spare.ps1 a11 text "hej"                        # skriv i fokuseret felt
spare.ps1 a11 find Battery                      # a11y-node (paalidelig A11, flaky A9)
spare.ps1 a11 update                            # self-update (se Play Protect nedenfor)
spare.ps1 a11 control                           # aabn browser-viewer: live skaerm + klik/tastatur
```

**Deploy (ny Husk-build) headless – bevist loop:**
1. Byg + signér + udgiv releasen normalt (RELEASE-PLIGT: begge `latest.json`-endpoints skal vise
   den nye `versionCode`). Spares henter fra `xplat.co/husk/latest.json` ligesom Note10.
2. `spare.ps1 <a9|a11> update` → foreground + download + commit.
3. `spare.ps1 <..> shot` for at se OS-dialogen:
   - **Play Protect »App scan recommended«** (fersk sideload): tap **More details**, `shot`, tap så
     **»Install without scanning«**. (Ingen »install anyway«-knap på disse enheder.)
   - **Standard »Do you want to install…«**: tap **Install**.
   - `find` er UPÅLIDELIG på disse dialoger (substring rammer »Don't install app« osv.) → koordinat-tap.
4. Installen commit'er → 8090 falder kort → **J4 self-healer** → `spare.ps1 <..> health` bekræfter.
   Ingen reboot nødvendig.

**Bemærk om Play Protect:** hver fersk sideload gates. Vil man have HELT ubemandet self-update på en
spare, kan Play Protect-scanning slås fra (Play Store → Play Protect → tandhjul → »Scan apps«), hvorefter
`/update`'s indbyggede `acceptInstallConsent` selv tapper standard-Install-knappen. Det er en
sikkerhedsindstilling – tag stilling pr. enhed (spares er dedikerede single-purpose-noder). Alternativt:
lær `acceptInstallConsent` »Install without scanning«-stien (kode-ændring → ny release).

**Interaktivt:** `http://<ts-ip>:8090/control` i en browser giver en live skærm-stream med
klik→a11y-tap + Tilbage/Hjem/Recents + tastatur – nul værktøj ud over browseren.
