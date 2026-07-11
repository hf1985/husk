# Husk-flåde + Termux-løs tailnet-transport

Dato: 2026-07-02. Kanonisk notat for hvordan Husk-enheder nås og styres **uden Termux**, plus
flåde-inventar og en ærlig reboot-gap-analyse for spare-enhederne. Pointer-memory:
`note10-meeting-camera`.

## 0. STATUS + NÆSTE SKRIDT (sidst rørt 2026-07-02, klar til pickup)

**AFKLARET denne runde:** Der findes INGEN headless software-vej til at genstarte de to spares
(.101/.102). Bevist fra flere vinkler (se §4). To ting fastslået udover »blokeret«:
1. **`/screen` (MediaProjection) er en SYNS-kanal uafhængig af a11y** – kan SE enhver Husk-enheds
   skærm skarpt selv når a11y-node-læsning er død (§6). Nyttigt til fjern-diagnostik.
2. **.102's a11y-motor kan HVERKEN læse ELLER injicere UI** (node-reads=kun navbar; `tap`/`swipe`=
   `ERR cancelled`; `home`/`back`=no-op; enheden var LÅST OP → ingen keyguard). Så både WD-recovery
   OG WD-pairing er umulige. .101 (A9) mangler WD helt. Tidligere »adbd binder loopback-only« var en
   over-konklusion og er rettet (blokeringen ligger opstrøms: UI'et kan ikke drives).

**ENESTE åbner = éngangs fysisk handling pr. telefon** (30 sek, når en spare er ved hånden):
USB → godkend RSA-nøgle (»tillad altid«) → `adb tcpip 5555` → derefter virker `adb reboot` headless
for altid, og hele recover-cyklussen kan PROVES som på Note10. Se §4 »Éngangs-oplåsning«.

**Fleeten er IKKE skrøbelig:** begge spares selvhelbreder efter enhver strøm-/manuel-reboot
(`BootReceiver` + a11y-auto-rebind overlever reboot); vi kan bare ikke fjern-TRIGGE beviset endnu.

**Løs ende:** .102's skærm kan stå fast på QS-shaden efter denne runde (mine gestus kunne ikke lukke
den, da injektion cancelleres) – harmløst på en ladende spare, nulstilles ved fysisk berøring/timeout.

**Ingen state-ændring udført på spares.** Tasks #22/#23/#24 lukket med denne konklusion.

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
- **Men syn ≠ kontrol.** På .102 er `/screen` læsbar, men a11y-gestus-injektion (`tap/swipe`) svarer
  `ERR cancelled` og `global home/back` er no-ops → jeg kan SE men ikke HANDLE. Derfor låser
  syns-kanalen IKKE reboot-benet op; den bekræfter blot præcist, at UI'et ikke kan drives.
- Carve-snippet (Git Bash + `py -3.11`): hent `/screen` → find `b'\xff\xd8'`..`b'\xff\xd9'` → skriv JPEG.

**Bivirkning observeret 2/7:** under forsøgene endte .102's skærm på en fastlåst QS-shade (mine
gestus kunne ikke lukke den igen, da injektion cancelleres). Harmløst på en ladende spare – nulstilles
ved næste fysiske berøring / skærm-timeout. Ingen state-ændring udført.
