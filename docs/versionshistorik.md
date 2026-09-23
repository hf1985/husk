<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Versionshistorik

Flyttet hertil fra `CLAUDE.md` 2026-09-19, ordret og uden at slette noget.

**Hvorfor den ikke må stå i `CLAUDE.md`:** den fil auto-loades i HVER agents kontekst i hele
porteføljen, og `check-claudemd-size.sh` målte kæden til 152.147 bytes mod et loft på 150.000.
Historikken er ren BEVIS-tekst - hvad hver udgave ændrede - og gatens egen kur er at beholde
påstanden i `CLAUDE.md` og flytte beviset til en fil der ikke auto-loades. Det er denne fil.

**Den aktuelle version står stadig i `CLAUDE.md`**, fordi det er en påstand om NUTIDEN som en
agent skal kende uden at slå op. Alt fra den forrige og bagud står her.

Tidligere: **1.0 / versionCode 51** (2026-09-07, bygget på `HFs-lenovo`. Tre ting: F-Droid-anmelderen `linsui` bad om at det står i UI'et at den indbyggede opdatering kommer direkte fra udvikleren og ikke fra F-Droid - nu en permanent note under knappen PLUS en bekræftelses-dialog der navngiver xplat.co + GitHub, og som KUN sidder på knappen, så den hovedløse `/update`-vej er uændret. Dertil de udskudte kode-fund: `Net.tailscaleIp()` mislabelede en mobiloperatørs CGNAT-adresse som Tailscale-IP - den kræver nu en `fd7a:115c:a1e0::/48`-adresse på SAMME interface. **Målt på Note10 FØR udgivelsen: `tun0` bærer både `100.100.103.102/32` og `fd7a:115c:a1e0::1536:c33a/128`**, så etiketten er stadig sand på en rigtig Tailscale-enhed; en carrier-CGNAT-adresse lander på `rmnet0` uden `fd7a:`. Danske strenge i HTTP-svar er oversat til engelsk - både `ControlServer`, `RigAccessibilityService` og de seks i `Hardware` (torch/volume/ringer/brightness/location/mic, som en adversarisk gennemgang fandt efter jeg havde erklæret oversættelsen færdig), plus de to serverede browser-kontrolsider. Samme fejl to steder mere er taget med: ntfy-pushen og de to notifikationskanal-navne var hardkodet dansk uanset enhedens sprog og er nu string-ressourcer (engelsk default, dansk følger enheden). Forældede header-kommentarer i `ControlServer`/`Net` om »binder ALDRIG 0.0.0.0« er rettet; bindingen selv er uændret. `Updater`s kommentar om at APK'en ligger på `objects.githubusercontent.com` er også rettet: updateren henter `husk-latest.apk` fra **main-grenen**, ikke et Release-asset - det er F-Droids `Binaries:` der henter release-assettet). Tidligere: 0.9.31/50 (ny release-nøgle + `vcsInfo { include false }` for reproducerbar F-Droid-build. Signaturskiftet kunne ikke bæres af in-app-updateren, så alle tre enheder blev afinstalleret og geninstalleret via adb - gjort 2026-09-04). Tidligere: 0.9.30/49 (token-gate for `/stream`, `/screen`, `/screen.mp4`). Tidligere: 0.9.29/48 (audit-runde 2 via Note10, log `docs/AUDIT-2026-07-12-runde2.md`: selv-review fangede en HIGH-regression jeg indførte i 0.9.28 – `acceptInstallConsent` læste stale `lastUpdate` → gentaget `/update` efter »latest« stallede; rettet m. synkron »checking«-reset + `sawProgress`-gate. Plus 3 LOW: /vibrate-loft, sensor-NaN-guard, InstallReceiver-fejl-synlighed. Note10-rig live-verificeret sund på 0.9.28: kamera/H.264/hardware/DeX/CSRF). Tidligere: 0.9.28/47 (stor sikkerheds+korrektheds+ydelses-audit 2026-07-12 – 3 parallelle review-agenter + manuel verifikation; beslutnings-log i `docs/AUDIT-2026-07-12.md`. Højdepunkter: CSRF/DNS-rebinding-forsvar i ControlServer; kamera-permanent-død + H.264-ANR + PackageInstaller-session-læk fikset; A14-sikker specialUse→camera-FGS selv-heal (uændret på ≤A13); motion-på-skærm-CPU-spild fjernet + Bitmap/BAOS genbrug. Ingen invariant A-D svækket). Tidligere: 0.9.27/46 (`acceptInstallConsent` lærte Play Protects »Install without scanning«-sti; on-device auto-accept KUN delvist pålidelig på spares pga. flaky a11y-`getWindows()` → pålidelig ubemandet self-update = Play Protect-scanning FRA ELLER PC-harness vision+tap; docs/fleet-tailnet-transport.md §7). Tidligere: 0.9.26/45 (reflekteret-XSS-fix); 0.9.25/44 (J4: `BootReceiver` håndterer `MY_PACKAGE_REPLACED` → 8090 rejser sig efter in-app-opdatering – bevist virksom på spares 2026-07-12). Ingen GitHub Actions i repoet (Gradle-buildet er verifikationen).

---

# Arkiveret fra FORTSÆT-HER.md 2026-09-20

Flyttet hertil ORDRET af spor `H7` i
`_styresystem/planer/2026-09-19-husk-udgiv-loekkefix-plan.md`.

⛔ **Her stod »uden at slette noget«. Det var FALSK, og rettelsen står i »anden del« nederst i
filen.** Flytningen tog linje 133-477 af en fil på 520 linjer; linje 104-132 og 478-520 blev
skrevet om i den nye handoff, og tre ting faldt ud undervejs - heriblandt en ejerbeslutning der
kun levede i en planfil. Den adversariske verifikation i rundens eget `/luk-runde` fandt det.
**Læs derfor denne overskrift som »linje 133-477«, ikke som »alt«.**

**Hvorfor:** `FORTSÆT-HER.md` auto-loades ved hver sessionsstart på denne flade og var vokset til
36.204 bytes. Ejerbeslutningen 2026-09-19 lyder »Gate nyt, ryd kun det der læses udefra eller ved
hver sessionsstart«, og handoffen er netop det sidste. Loftet er 12.000 bytes.

**Det der IKKE står her, står stadig i handoffen:** de to ting der kræver et menneske, de tre
BESLUTTEDE opgaver uden levende peger, og de to åbne fund fra 2026-09-19. Kun det der var
historik er flyttet.

Blokken nedenfor er linje 133-477 af filen som den så ud før flytningen. Den bærer sine egne
overskrifter og sine egne datoer; læs den som et øjebliksbillede, ikke som nutid. Flere af dens
afsnit beskriver en tilstand der siden er ændret - navnlig at flåden var ensartet på 1.0/51, og
at F-Droid-indsendelsen var MR !40810.

## Status (historik frem til 1.0)

**Udgivet og i drift.** Tidligere version **1.0 / versionCode 51** (2026-09-07, bygget og
signeret på `HFs-lenovo`). Udgives på GitHub-releases (`hf1985/husk`) og `xplat.co/husk`;
**F-Droid er endnu IKKE udgivet** - fdroiddata-MR !40810 er ÅBEN og afventer anmelderne.
Pure framework, ingen AndroidX, ingen afhængigheder.

**Hvorfor 1.0 kom.** F-Droid-anmelderen `linsui` skrev 07-09 kl. 12:58 det sidste krav:
»Please also make it clear in the UI that the update is from you directly.« Hans første ønske
blev løst metadata-only; dette kunne ikke, fordi det handler om appens egen skærm. Derfor blev
de tre udskudte kode-fund taget med i samme bump (de krævede alle et versionsbump og var holdt
tilbage for ikke at kassere en bestået testkørsel).

> **Målt efter udgivelsen 2026-09-07:**
> - Begge `latest.json`-endpoints viser `versionCode 51`, `/husk/openapi.json` melder 1.0 med
>   44 paths, `pc/check-api-parity.sh` er grøn.
> - **F-Droids egen pipeline `2827292807` er GRØN** på fork-head: »Successfully built
>   co.xplat.husk:51 from 02e069b7…«, »compared built binary to supplied reference binary
>   successfully«, med den pinnede signer `96195cfd…c17d`.
> - **Enhederne kører 1.0** (`/info`: `v1.0/51`, `a11y: true`). Bevist at det ER den nye kode og
>   ikke bare et versionsnummer: `/update` svarer »remote self-update started … read /flags« og
>   `/screen.jpg` svarede uden frame »no screen frame (turn on screen sharing in the app)« -
>   begge på ENGELSK, hvor 0.9.31 svarede på dansk. Et versionsnummer kan komme fra en cache;
>   svarets sprog kan ikke.
> - **`tailscaleIp` er IKKE regredieret:** riggen på 1.0 rapporterer stadig
>   `tailscaleIp=100.100.103.102`. Kravet om en `fd7a:`-markør på samme interface er målt
>   holdbart, fordi `tun0` bærer både `100.100.103.102/32` og `fd7a:115c:a1e0::1536:c33a/128`.

> - **UI-teksten er set på skærmen, ikke kun i ressourcerne.** Sony 702SO på 1.0 viser knappen
>   »UPDATE HUSK (DIRECT FROM THE DEVELOPER)« med hele noten under, og et tryk giver dialogen
>   »Update from the developer, not F-Droid« med knappen »DOWNLOAD FROM THE DEVELOPER«.
>   Skærmbilleder er taget; a11y bekræftede noderne uafhængigt.

**HELE flåden kører 1.0/51 (2026-09-07).** Den sidste enhed, Samsung SM-A102U1, blev taget af
ejeren; de to andre fjernstyret. Målt på alle tre bagefter: `versionCode 51`, `rpc ping` = PONG,
`a11y: true`, og `/snapshot` 200 med et rigtigt JPEG (92 kB på A9, 355 kB på A11, 208 kB på
Note10). **Alle tre rapporterer stadig deres rigtige `tailscaleIp`**, så fd7a-kravet er nu
efterprøvet på Android 9, 11 og 12 - tre uafhængige enheder, tre OS-generationer.

Årsagen til at alle tre først stallede står i nøgleskifte-opskriften nedenfor: »Installer
ukendte apps« var slået FRA. Sony blev kureret fjernstyret (SETTINGS → toggle → **tilbage ÉN
gang**, ikke back+home, som forlader install-sessionen) og tog derefter opdateringen i første
forsøg.

⚠️ **En in-app-opdatering efterlader TO tjenester nede, og ingen af dem melder en fejl.**
Målt på flåden 2026-09-07 efter 1.0 landede. Begge er efterslæb fra processudskiftningen, ikke
noget 1.0 indførte - `BootReceiver`s `MY_PACKAGE_REPLACED`-vej rejser 8090, og det gør at ALT
ser sundt ud udefra:

- **Skærmdeling (MediaProjection).** Samtykket dør med processen. Note10 og 702SO kom tilbage af
  sig selv; `SM-A102U1` svarede »no screen frame« i timevis og er nu tilbage - men det kan
  IKKE attribueres, fordi ejeren rørte telefonen samtidig med at en fjernstyret toggle-cyklus
  var i gang. Skriv den ikke ned som en virksom kur uden en ren genmåling.
- **Kamera (`CameraService`).** Efter opdateringen leverede kameraet ikke på NOGEN af de to
  spares: `/snapshot` svarede 503 »no frame yet« også på andet kald, mens `/screen.jpg` virkede
  og porten var oppe. Kuren er et tap på »Camera streaming« i appen; derefter svarede begge 200
  (94-346 kB). Note10 var upåvirket.
  ⚠️ **»Fordi `ScreenService` hostede 8090 alene« stod her som forklaring indtil 2026-09-08 og
  er en HYPOTESE, ikke en måling.** `BootReceiver` starter `CameraService` ubetinget ved
  `MY_PACKAGE_REPLACED`, og det er den der normalt hoster 8090, så forklaringen er ikke engang
  den mest sandsynlige. Ingen målte `dumpsys activity services`, og der findes ingen måling af
  hvorfor det ramte A9+A11 men ikke A12. **Mål det næste gang det sker** frem for at arve
  forklaringen.

**Efter enhver in-app-opdatering: efterprøv `/snapshot` OG `/screen.jpg` pr. enhed.** Hverken
`/flags` eller `/info` kan afsløre det - se næste punkt.

⛔ **`/flags` `camera` er MÅLT ubrugelig som diagnose tre gange på én dag.** Den er
`Rig.cameraRunning`, som kun er sand mens capture faktisk kører, så den stod `False` på alle tre
enheder SAMTIDIG med at Note10 leverede et 27 kB JPEG, og den stod `False` umiddelbart efter at
et tap havde genstartet kameraet på `SM-A102U1` - som så svarede 346 kB. **Døm på `/snapshot`s
svar, aldrig på flaget.**

Bemærk også at den gemte toggle-koordinat `953,1222` fra nøgleskiftet er FORÆLDET: noten under
Opdatér-knappen skubber alt nedenunder ned. Har enheden skærmdeling, så find koordinaten ved
kørsel med `/find?match=...` frem for at genbruge et tal.

> ⚠️ **»Ingen adfærdsændring« om 0.9.31 var FORKERT, og stod her indtil 2026-09-06.**
> `git diff v0.9.30 v0.9.31 -- app/` bærer også `ControlServer.java:218`:
> `dparam(query,"topic") != null` → `param(query,"topic") != null`. `dparam` returnerer `""`
> og aldrig `null` (linje 441-442), så guarden var ALTID sand: ethvert `/motion`-kald uden
> `topic` nulstillede `Rig.ntfyTopic` og persisterede det, så bevægelses-alarmen tavst holdt
> op med at sende push. Fixet er `6a07d64`, forfader til `v0.9.31` men ikke til `v0.9.30`.
> **GitHub-release-teksten for v0.9.31 bærer stadig den falske sætning** - se sporet i
> rundens lukkeplan.

**Signeringsnøglen blev skiftet i 0.9.31.** Den gamle var en genbrugt
debug-keystore hvis kodeord stod i klartekst i `docs/BUILD.md` i dette
OFFENTLIGE repo; F-Droid pinner nøglen permanent, så vinduet var FØR første
publicering. Ny nøgle: `CN=xplat`, RSA 4096, SHA-256 `96195cfd…c17d`, i
vaulten (se `docs/BUILD.md` §5). **Springet fra 0.9.30 til 0.9.31 kunne
ikke bæres af in-app-updateren** – Android afviser en signaturændring – så alle tre enheder blev
afinstalleret og geninstalleret via adb. **Det er gjort (2026-09-04); flåden er ensartet.**

Flåden er tre fysiske enheder: Note10+ SM-N975U1 (Android 12, DeX, token,
`.103.102`) plus to spares, Sony 702SO (A9, tokenløs, `.101.101`) og Samsung
SM-A102U1 (A11, tokenløs, `.101.102`).

Husk er **motoren**; `husk-overbygning`, `P_app_phone-devbox` og `P_kontor`
bygger ovenpå, og `P_add-on_phone-transport` er hvordan man når telefonen.

## Det der oftest går galt, og som skal stå her

**En kodeændring er IKKE færdig før den er UDGIVET.** Committet-men-ikke-udgivet
betyder at de fysiske telefoner stadig kører den gamle kode. Det er sket:
XSS-fixet `e999ae4` blev committet og aldrig releaset, så alle enheder kørte
videre på 0.9.25 uden det.

**Updateren spørger `https://xplat.co/husk/latest.json` FØRST** (GitHub-raw er
kun fallback). Glemmes xplat.co-deployen, svarer enhederne »allerede nyeste« selv
om GitHub er opdateret. **Begge** endpoints skal vise den nye `versionCode`.

Den fulde otte-trins release-huskeliste står i `CLAUDE.md`; gentag den ikke her.
Definition af færdig: begge `latest.json`-endpoints viser den nye version,
F-Droid-pipelinen er grøn, **og** `pc/check-api-parity.sh` er grøn med `/husk/api`
ajourført.

## Verificeret / ikke verificeret

- **Fire ydelses-invarianter (A-D) må ALDRIG rulles tilbage.** De blev alle
  indført efter konkrete CPU- og frys-regressioner: doven skærm-streaming, smal
  a11y-maske, dovent kamera uden eviction, og **ingen cross-display launch**
  (invariant D er selv en fjernelse – v0.9.21's display-0-bounce triggede
  Samsungs »genstart på anden skærm«-dialog og crashede scrcpy og Discord på
  DeX). Læs `docs/YDELSE-OG-DRIFT.md` før du rører Screen, Camera eller a11y.
- **Spares kan itereres fuldt** – det tidligere »umuligt« var en fejldiagnose.
  En idle spare sover skærmen, så a11y kun ser navbaren og gestus svarer
  `ERR cancelled`; det blev læst som en død motor. **Kuren er `wake` FØRST.**
  Harness: `pc/spare.ps1` og `pc/spare.sh`.
- **Node-læsning er pålidelig på A11 og flaky på A9** – begge spares drives
  derfor via syn plus koordinat-tap, ikke via node-opslag.
- **Rest-gab:** en ren `adb reboot` på en spare kræver stadig et engangs-USB-kabel.
  Det er ikke nødvendigt for iteration. Fuld analyse:
  `docs/fleet-tailnet-transport.md`.

## Næste skridt

**1. Flåden er ENSARTET igen (2026-09-07): alle tre på 1.0/51.** Se det målte i Status ovenfor.
**Intet udestår på jern.** Alle tre svarer nu PONG, leverer et JPEG på BÅDE `/screen.jpg` og
`/snapshot`, og rapporterer deres rigtige `tailscaleIp`. Skal en spare opdateres igen, så husk
de to ting der begge staller TAVST: »Installer ukendte apps« skal være slået TIL, ellers går
installen aldrig igennem, og efter installen skal `/snapshot` og `/screen.jpg` efterprøves pr.
enhed, fordi kamera- og skærmtjenesten kan ligge nede uden at `/flags` viser det.

> **Nedenstående gjaldt nøgleskiftet 2026-09-04, hvor alle tre stod ens på 0.9.31/50.**
> Note10 blev geninstalleret af sessionen, de to spares af ejeren. Målt via `/info` bagefter:
> Sony 702SO (A9, sdk 28) og Samsung SM-A102U1 (A11, sdk 30), begge `versionName 0.9.31`,
> `versionCode 50`, `a11y: true`, `screen: true`, `batteryOptIgnored: true`, og `/snapshot` 200
> med et rigtigt JPEG (83-97 kB på A9, 380-405 kB på A11).

> **Opskriften er BEVARET her, fordi den gælder ethvert fremtidigt nøgleskifte** - ikke kun dette.
> Afinstallationen tager mere med sig end app-data:
> - **Køretids-tilladelser nulstilles.** `/snapshot` svarer 503 »no frame yet« indtil
>   `pm grant co.xplat.husk android.permission.CAMERA` (samt `RECORD_AUDIO`, de to `*_LOCATION`,
>   `POST_NOTIFICATIONS`).
>   **⛔ `camera`-flaget i `/flags` kan IKKE bruges som diagnose - det er MÅLT forkert 2026-09-04.**
>   Flaget er `Rig.cameraRunning`, som først sættes når capture reelt kører
>   (`CameraService.java:320`). Mangler tilladelsen, fejler `openCamera`, flaget bliver aldrig
>   sandt, og `/snapshot` svarer 503 fordi `latestJpeg` er null (`ControlServer.java:263`).
>   **`camera:false` + 503 er derfor byte-identisk i »dovent, endnu ikke åbnet« og i »tilladelsen
>   mangler«.** Her stod indtil 2026-09-04 at man skulle kalde to-tre gange og se på flaget; det
>   er en diagnose der ikke kan fejle, altså ingen diagnose (måleregel 8).
>   **Spørg i stedet det lag der VED det:** `adb shell dumpsys package co.xplat.husk | grep -i CAMERA`
>   viser `granted=true|false` direkte. Alternativt: se om flaget SKIFTER til sandt efter et kald.
> - **a11y-registreringen ryddes.** `settings put secure enabled_accessibility_services
>   co.xplat.husk/co.xplat.husk.RigAccessibilityService` + `accessibility_enabled 1`, og **den
>   binder først ved næste reboot**.
> - **Batteri-undtagelsen ryddes.** `dumpsys deviceidle whitelist +co.xplat.husk`.
> - **`dex_reconnect` og `screen_share` ryddes.** Den første sættes hovedløst
>   (`am start -n co.xplat.husk/.MainActivity --ez dexreconnect true --ez finish true`).
>   Den anden kan ikke sættes gennem `ScreenConsentActivity` (`exported="false"`), men kræver
>   **ikke** et menneske: `am start` på `MainActivity`, `adb exec-out screencap -p` som ØJNE, og
>   `adb shell input tap` som FINGER. Toggle'en »Screen sharing (keep on)« lå på `953,1222` i
>   1080x2280 på Note10. Husks egen a11y-motor accepterer derefter MediaProjection-dialogen selv.
> - ⛔ **»Installer ukendte apps« ryddes OGSÅ - og det er den der tavst dræber self-update.**
>   MÅLT 2026-09-07 på begge spares: `/update` hentede 1.0 og satte `lastUpdate` til
>   »install requested«, men installen skete aldrig. På skærmen stod
>   »For your security, your phone is not allowed to install unknown apps from this source«
>   med knapperne CANCEL og SETTINGS. **`acceptInstallConsent` kan ikke klare den dialog**:
>   den tapper efter Install/Update/Opdater, og kuren her er SETTINGS efterfulgt af en toggle
>   i Indstillinger. Symptomet ligner »Play Protect gater sideloads«, men er en anden sag.
>   `lastUpdate` bliver ved med at sige »install requested«, fordi der aldrig kommer en
>   terminal status - der er altså INTET fejlsignal i `/flags`. Fjern-kuren:
>   `/find?match=(?i)^settings$` → `/tap` → `/find?match=Allow from this source` → `/tap`,
>   og gå så TILBAGE ÉN gang (ikke back+home, som forlader install-sessionen).
>   Efter en afinstallation skal den altså sættes igen, ellers kan enheden aldrig
>   fjern-opdatere sig selv.
> - **Tokenet overlever** (det bor i `Settings.Global husk_token`, ikke i app-prefs).
> - **adb skal gå DIREKTE til adbd**, ikke gennem Husks bro på 15557: broen er en del af appen og
>   dør i det sekund man afinstallerer. **Find derfor WD-porten FØR afinstallationen** med
>   `adb connect 127.0.0.1:15557` og `adb devices` (den direkte `127.0.0.1:<wd>` står da på listen),
>   eller efter en reboot når a11y har genrejst WD. Uden den sætning er punktet en advarsel uden
>   kur, og netop den kur er det der gør et nøgleskifte kørbart uden et USB-kabel.

**2. xplat.co ER deployet** (senest 2026-09-08). Begge `latest.json`-endpoints viser
`versionCode 51`, `/husk/openapi.json` melder 1.0 med 44 paths, og
`pc/check-api-parity.sh` er grøn. Release-pligtens trin 8 er dermed opfyldt.

> ⛔ **Men grøn parity er IKKE en ajour API-doc.** `pc/check-api-parity.sh` siger det selv i sit
> hoved: den ser endpoint-NAVNE og `versionCode`, aldrig params, respons eller adgangsmodel.
> En adversarisk gennemgang 2026-09-08 fandt seks ting kataloget skyldte: `/update`s svar stod
> stadig på dansk, `/set` manglede `sq`+`sfps`, `/key` manglede `enter`, `/rpc` manglede
> `text`/`enter`/`wake`, flere fejl-svarformer var udokumenterede, og API-doc'ens intro påstod
> at kun Tailscale-nettet kan nå serveren, hvilket butiksteksten samtidig modsiger.
> **Læs katalogets `resp`- og `params`-felter mod koden i hånden ved hver release.**

> **Fælde værd at huske:** deployet blev først fejlagtigt meldt umuligt, fordi
> `~/.ssh/agent.env` ikke fandtes i WSL. Det er den forkerte prøve.
> `hosting-deploy.sh` skal køres fra **Git Bash** (vault2 virker kun dér) og
> bruger selv `scripts/deploy-asura/wsl-transport.sh` som bro til den private
> Asura-nøgle, der kun ligger i WSL (`~/.ssh/khfrb_asura_openssh`).
> Én negativ prøve på ét sted er ikke et bevis for manglende adgang.

> **Samme fælde en gang til, samme dag:** `screen_share` blev meldt som »kræver
> et menneske«, fordi `ScreenConsentActivity` ikke er exported. Men adb giver
> både syn (`screencap`) og berøring (`input tap`), så appens egen UI kan betjenes
> uden a11y og uden en person. CLAUDE.mds advarsel mod at forgrunde `MainActivity`
> på den kørende rig holdt IKKE her: efter tryk + `KEYCODE_HOME` var a11y stadig
> PONG, `/snapshot` gav 200 (230 kB), `/screen.jpg` 200 (82 kB) og adb-broen levede.
> Advarslen gælder DeX-churn, og det er uvist om DeX var tilsluttet under målingen,
> så den er ikke modbevist - kun konstateret uskadelig i dette tilfælde.

**Deploy til den kørende rig:** `adb install -r <apk>` når adb eller WD er sund,
derefter `adb reboot` for en ren fuld tilstand. **Launch aldrig `MainActivity`
via `am start` på den kørende rig** – det forgrunder Husk nær DeX og slår a11y
midlertidigt fra. Verificér i stedet via `/snapshot` (to kald - kameraet er dovent), **ikke via
`/flags`s `camera`-felt**, som er målt ubrugeligt som diagnose (se advarslen ovenfor).


## 📜 HISTORIK: F-Droid MR !40810 (1.0, indsendt 07-09-2026)

> ⛔ **FORÆLDET.** !40810 blev MERGET 15-09-2026, og 1.1 gik gennem den NYE MR !49350
> 19-09-2026. Alt herunder gælder 1.0-indsendelsen og er bevaret som historik.

**Aktuel tilstand (målt 2026-09-08):** recipe'ens build-entry peger på `02e069b` (1.0 / 51),
fork-head er `e430655`, pipeline **2827292807** er grøn med reproducerbar byte-match, og
MR-labelen er `review-requested`. Svaret til `linsui` blev postet 07-09 kl. 18:10Z.

> **Forhistorien, som forklarer hvorfor 1.0 kom.** Testeren `gitubpatrice` kørte 06-09 en fuld
> gennemgang (Galaxy S9, API 29, ingen tilladelser givet) og bestod: install, koldstart,
> reproducerbarhed og en netværks-måling. `linsui` svarede samme aften med den første betingelse
> (»make it clear that the update is not from F-Droid«), som blev løst METADATA-ONLY. Dagen
> efter kom den anden: »Please also make it clear **in the UI** that the update is from you
> directly«. Den kunne ikke løses i butiksteksten, og derfor blev 1.0 bygget.

> **Den FØRSTE betingelse blev løst METADATA-ONLY** (historik): butiksteksten fik et
> Opdaterings-afsnit, og der kom changelogs for versionCode 50, uden nogen ændring under
> `app/`. Formen er værd at kende, for den gælder hver gang en anmelder beder om noget der kan
> siges i teksten: **APK'en forbliver byte-identisk, så en netop bestået testkørsel og
> reproducerbarheden står**, mens et versionsbump ville have kasseret begge dele og sendt MR'en
> bagerst i en lang kø. Den er brugt igen 2026-09-08 til at rette en falsk changelog-sætning.

> **Fælden der blev fanget FØR den nåede F-Droid.** Min første butikstekst sagde »It only ever
> runs when you press it«. Det er FALSK: `Updater.checkAndUpdate` har to indgange, knappen
> (`MainActivity.java:110`) OG `GET /update` (`ControlServer.java:369`), og sidstnævnte lader
> a11y kvittere for install-dialogen selv. Den adversariske gennemgang af svar-udkastet fandt
> det; teksten er rettet i `dde86ba`. **Ingen automatik findes dog:** ingen `AlarmManager`,
> `JobScheduler` eller `WorkManager`, og `BootReceiver` kalder den ikke.

**Køen til NÆSTE release (kræver alle et versionsbump):**
- **`peerAllowed()`/token:** tokenet gater hele API'et via `dispatch()` (kun `/healthz` og `/`
  er fri), men `tokenOk()` returnerer true når tokenet er tomt. Obligatorisk token + en
  skarpere `peerAllowed` hører sammen i én release, fordi det bryder hver eksisterende enhed
  indtil den er re-paret.
  ⛔ **»Hver eksisterende enhed« omfatter enhver PC der bruger telefonen som webcam** – det
  produkt beskriver i dag tokenet som valgfrit, og en obligatorisk token gør `401` til den
  almindelige tilstand dér frem for undtagelsen. Skrevet ind i dets handoff 2026-09-22, så
  ændringen ikke kommer bagfra. Releasen er altså IKKE kun et flåde-anliggende.
- ✅ **BORTFALDET i 1.1:** »ingen fejlsignal når Installer ukendte apps er slået fra«.
  Hele updateren er fjernet, så tilstanden kan ikke opstå.
- ✅ **RETTET i 1.1:** `dparam(query,"server") != null` var altid sand, fordi `dparam` giver
  `""` og aldrig `null` - præcis 0.9.31's `topic`-fejl. Genmålt 2026-09-19, stadig til stede,
  og skrevet om til `param(...)`.

> ✅ **Udkom i 1.0:** `Net.tailscaleIp()`s carrier-CGNAT-mislabel og de danske strenge i
> HTTP-svar. Stod her som kø-punkter indtil 2026-09-08.



## 📜 HISTORIK 2026-09-18: MR'en er MERGET, men F-Droids review-kit melder ni fejl

> ✅ **LUKKET 2026-09-19 i 1.1.** Alt herunder er bevaret som historik. De ni fejl er rettet.
> ⛔ **Her stod »MR !49350 er åbnet, og status står i afsnittet øverst«.** Begge dele er nu
> vildledende: MR'en blev MERGET 20-09-2026, og »afsnittet øverst« er i DENNE fil historikken
> frem til 1.0, som stadig kalder !40810 åben. Nutiden står i `FORTSÆT-HER.md` og i registeret
> 2026-09-22 nederst i denne fil. Læs dem FØRST.

Skrevet af en ad hoc-runde på `HFs-lenovo` der arbejdede i `P_app_husk-viewer`. Runden rørte
**ingen** kode her; den målte tilstanden og lagde arbejdet i en plan.

**Afsnittet »F-Droid MR !40810: 1.0 er indsendt, bolden ligger hos F-Droid« er forældet.**
Bolden ligger hos os. To ting er sket siden:

1. **MR !40810 blev MERGET 15-09-2026 kl. 08:49** af `linsui` (mail i tråd `19eded065e4c820c` på
   `hf@brobjerg.dk`, besked `1a0a4420f407536e`). En tester (`dowardev`) havde bekræftet 1.0/51 på
   en Xiaomi 23129RA5FL med signatur-match.
2. **Fire timer senere, kl. 13:20, postede `LiberiFatali` en `fdroid-review-kit`-rapport med
   dommen ❌ FAIL: 9 fejl, 1 advarsel, 5 spørgsmål** (note_3836453313). En merge er altså ikke en
   ren anmeldelse, og rapporten kom EFTER merget - læs aldrig merget som et grønt lys.

**De ni fejl, målt mod repoet 2026-09-18 og alle stadig åbne:**

| Fund | Målt tilstand | Kommando |
|---|---|---|
| 1. `Builds.commit` er ikke det taggede commit | tag `v1.0` → `02e069b763f5`, recipens `commit:` → `b75af7a07c5d` | `git rev-parse 'v1.0^{}'` og `grep commit: fdroid/co.xplat.husk.yml` |
| 2. Prebuilt APK i kildetræet | `husk-latest.apk` er **sporet i git** | `git ls-files \| grep -i '\.apk$'` |
| 3-8. Selv-opdaterings-kode | `PackageInstaller` i **6** filer; `Updater.java` 164 linjer, `InstallReceiver.java` 53 | `grep -rln PackageInstaller app/src/main/java/co/xplat/husk/` |
| 9. `REQUEST_INSTALL_PACKAGES` | erklæret på linje 23 | `grep -n REQUEST_INSTALL_PACKAGES app/src/main/AndroidManifest.xml` |

Dertil advarsel 10 og spørgsmål 11-15: hver netværksvært skal gøres rede for i butiksteksten
(`0.0.0.0`, `127.0.0.1`, `ntfy.sh`, `xplat.co` - og `raw.githubusercontent.com`, som kun findes i
`Updater.java` og forsvinder med fund 3-8).

**To følger der skal læses FØR nogen går i gang:**

- **Flåden mister in-app-opdatering.** F-Droids inklusionspolitik tager ikke selvopdaterende apps,
  så `/update`-ruten, `Updater.java` og tilladelsen skal væk. Opdatering sker derefter gennem
  F-Droid-klienten eller `adb install` over husets egen Termux-ADB. Ejeren har besluttet det,
  verbatim 2026-09-18: »Når du opgraderer appen, så sørg også for at implementere alle rettelser
  fra fdroids seneste emails«.
- **Husets egen RELEASE-PLIGT modsiger F-Droid.** `CLAUDE.md`s trin 3 foreskriver at repoet skal
  bære den signerede `husk-latest.apk`. Det er ordret det fund 2 afviser. Reglen skal rettes i
  samme ombæring, ellers genindfører næste release fejlen.

**Arbejdet er skrevet som en køreklar plan, ikke som spor her:**
`_styresystem/planer/2026-09-18-husk-webcam-produkt-plan.md`, sporene `H1`-`H7`.
Planen lukker de ni fejl OG tilføjer `/set?front=0|1` i samme release (1.1 / versionCode 52),
fordi PC-siden ellers skal vælge forsidekamera gennem WSL → SSH → Termux → ADB, og den kæde kan en
fremmed bruger ikke have. `Rig.useFront` findes allerede (`Rig.java` l. 39) og læses i
`CameraService.openCamera()` (l. 255), så ændringen er lille - men den skal lukke den åbne
kamera-enhed ved et SKIFT, ellers slår valget først igennem ved næste dovne genåbning.

⚠️ **Rækkefølgen er bindende:** `P_xplat`s `HUSK_API`-katalog skal ændres FØR releasen, fordi
`pc/check-api-parity.sh` er en release-gate der læser katalogfilen og sammenligner både endpoints
og versionCode med appen. Planens spor `X1`-`X2` kommer derfor før `H5`-`H6`.

## Adversarisk verifikation (ad hoc-runde `husk-loekkefix-og-korthed`, 2026-09-19, `HFs-lenovo`)

Frisk sub-agent (`fable`), syv linser, uden orkestratorens konklusioner.
Runden havde ingen plan, så dommene står her.

| Linse | Dom | Hvad der blev målt |
|---|---|---|
| 1. Er planernes tal sande? | **REFUTERET: delvist** | 13 tal holdt ved genmåling; 4 gjorde ikke. `MAALEREGLER.md` var forkert allerede ved commit, `CLAUDE.md` var forældet af en nabo, commit-medianen er 1.321,5 (ikke 1.369), og C#-bentallet er 133 talt statisk og **ikke kørt**. linsuis tid er UTC. |
| 2. Er planerne kørbare uden at spørge? | **REFUTERET: ja** | `sync-config-homes.ps1` ligger i `hooks/`, ikke `scripts/`. `K4`s værktøj måler ikke `CLAUDE.md`. `K1` genindførte den pensionerede nøgle `plan-loft`. To lukke-betingelser krævede »grøn« på tjek der var røde i forvejen. `H1`s rebase-præmis var forkert. |
| 3. Lukke-betingelser der ikke kan fejle | **REFUTERET: ja** | `V1` og `H1` hvilede på en dom frem for en måling; begge er nu grep-ankrede. Luk-sporene `H8`/`V6`/`K7` bærer bevidst ingen betingelse. |
| 4. Gate 4 og gate 11 | **REFUTERET: ja** | `infra/gitlab.md` manglede at MR-metadata svarer 200 anonymt, og at en squash-merget MR efterlader kildegrenen konfliktende. Begge er skrevet ind. |
| 5. Kolliderer runden med en nabosession? | **REFUTERET: delvist** | Tre nabo-commits samme aften rørte `CLAUDE.md`, `MAALEREGLER.md` og `check-claudemd-size.sh`. `GRAENSE` er hævet 150.000 → 200.000, så måleregel-ruten er åben igen. |
| 6. Lander `SVAR`-blokkene? | **REFUTERET: nej** | `check-svar-landet.sh`: 97 blokke, 69 landet, 28 FUND – ingen af de 28 er `M-2026-09-19-01/-02/-04`. Spor-id'erne findes i de navngivne planer. |
| 7. Er der en fjerde ting? | **REFUTERET: delvist** | 1.1 indførte **to** fejl i `requestFront`, ikke én, og `Rig.useFront` persisteres ikke. Begge står nedenfor. |


---

# Hvorfor release-pligten ser sådan ud

`CLAUDE.md`s release-blok bærer PÅSTANDEN; her står de hændelser der er grunden til hvert krav.
Flyttet hertil 2026-09-20 af spor `H7`, fordi `CLAUDE.md` auto-loades i hver agents kontekst og
skulle under 12.000 bytes. Intet krav er ændret, kun beviserne er flyttet.

- **»Stop ALDRIG ved commit«** – XSS-sikkerhedsfixet `e999ae4` blev committet men aldrig releaset,
  så alle enheder kørte videre på 0.9.25 uden fixet. Det er den hændelse hele release-pligten er
  skrevet efter.
- **API-DOK-GATEN (trin 6)** – erfaring 2026-07-12: en release bumpede versionen men glemte fem nye
  endpoints plus CSRF-modellen i `/husk/api`. `pc/check-api-parity.sh` fanger netop dét.
- **»Læg ALDRIG APK'en i repoet igen« (trin 3)** – F-Droid-fund 2 af 15-09-2026: binære filer i
  kildetræet kan ikke revideres. `husk-latest.apk` blev fjernet i 1.1, og `.gitignore` dækker `*.apk`.
- **Changelog-kravet (trin 3b)** – påpeget af F-Droid-testeren 06-09-2026: med
  `AutoUpdateMode: Version` lander en release uden `changelogs/<versionCode>.txt` med et tomt
  »What's New« i F-Droid.
- **»Tagget SKAL sidde på præcis byggecommiten« (trin 7)** – F-Droid-review-kittets fund 1:
  `v1.0` sad på `02e069b`, mens `b75af7a` var den commit der blev bygget.
- **Flåden opgraderes gennem F-Droid-klienten eller `adb install`, aldrig on-phone build.** Her stod
  indtil 1.1 »via in-app Updater«; den findes ikke mere (F-Droid-fund 3-9).
- **`check-api-parity.sh` læser IKKE `latest.json`.** Indtil 2026-09-19 stod der i `CLAUDE.md` at
  gaten var en af `latest.json`s læsere. Det var falsk og ville have holdt en fil i live på en grund
  den ikke har: gaten sammenligner `app/build.gradle` med `P_xplat`s `HUSK_VERSION_CODE`.

---

# Arkiveret fra FORTSÆT-HER.md 2026-09-20, anden del

⛔ **Denne del findes fordi den FØRSTE flytning ikke var udtømmende, og fordi den påstod at være det.**
Afsnittet ovenfor skriver »linje 133-477 af filen som den så ud før flytningen« og »uden at slette
noget«. Den gamle fil havde **520 linjer**. Linje 104-132 og 478-520 blev hverken arkiveret eller
båret ordret med over i den nye handoff - de blev skrevet om, og tre ting faldt ud undervejs.
Fundet af den adversariske verifikation i samme rundes `/luk-runde`, målt med `grep -F` mod alle
tre filer: nul træf.

**Det der var ved at gå tabt, og hvorfor det betyder noget:**

1. **Ejerens beslutning `M-2026-09-19-04` ordret.** Den levede kun i planfilen
   `2026-09-19-husk-udgiv-loekkefix-plan.md` (`B3`), og en plan slettes ved sit runde-luk. En
   beslutning skal stå dér hvor den bliver LÆST (måleregel 66), og den næste der kigger på
   1.0-entryen, kigger her - ikke i en slettet plan.
2. **Begrundelsen for at 1.0-entryen ikke flyttes.** Uden den ser beslutningen vilkårlig ud, og
   næste session kan »rette« den i god tro.
3. **To målte kapabiliteter om flåden** som ikke står andre steder: at skærmdeling IKKE overlever
   en frisk installation, og at `adb pair` virker over tailnettet mens Husks egen bro dør med en
   afinstallation.

Blokkene står ORDRET som de stod i `1116a84`.

### Bolden hos F-Droid

> ⛔ **AFLØST 2026-09-19. Spørgsmålet er BESVARET af ejeren og skal FJERNES fra MR-beskrivelsen.**
> `M-2026-09-19-04`, verbatim: »Lad entryen stå på b75af7a, og fjern spørgsmålet fra MR'en«.
> Entryen bliver altså stående, og anmelderen skal ikke afgøre det.
> Afsnittet nedenfor er bevaret som begrundelsen, ikke som en åben sag.

MR !49350 indeholdt ét åbent spørgsmål til anmelderen: **skal 1.0-entryen pege tilbage på
`v1.0` (`02e069b`)?** `app/` er byte-identisk mellem `02e069b` og entryens `b75af7a`, så APK'en
er den samme - men `02e069b`s changelog for 51 påstår at server-svar og kontrolsider følger
enhedens sprog, hvilket ikke passer. Det er derfor entryen IKKE blev flyttet. Svarer anmelderen
at de hellere vil have `Builds.commit` = tagget, er det én linje.

### Sådan blev 1.1 prøvet (så en genlæser ikke skal gætte)

Spare SM-A102U1 (Android 11) over Tailscale, både som opgradering fra 1.0 **uden tab af
konfiguration** (en bevidst ikke-default motion-config overlevede) og som **frisk installation**
(hvor den samme config forsvandt - kontrollen der gør opgraderings-benet troværdigt).
`/set?front=1` → `/flags.front` sand → `/snapshot` fra forsidekameraet, bevist reproducerbart
på lysstyrke. ⚠️ **Skærmdeling overlever IKKE en frisk installation** - MediaProjection-samtykket
skal gives igen, og det blev gjort med et adb-tap.

⚠️ **Ny adb-parring på `HFs_Dell`.** Maskinen var ikke parret med spare-telefonen. Parringen
sker over Tailscale: `/pair` giver adresse og kode, og `adb pair <tailscale-ip>:<port> <kode>`
virker - pairing-porten er nåelig over tailnettet, ikke kun på LAN. Derefter både
`adb connect <ts-ip>:15557` (Husks egen bro) og **direkte til WD-porten**. Den sidste er vigtig:
broen dør med en afinstallation, så en frisk installation kun kan gennemføres over den direkte
forbindelse.

## Register 2026-09-19 (ad hoc-runde `husk-loekkefix-og-korthed`, `HFs-lenovo`): MR'en er blokeret, og 1.1 bar TO fejl

**Arbejdet er lagt i to planer, ikke her:**
`_styresystem/planer/2026-09-19-husk-udgiv-loekkefix-plan.md` (spor `H1`-`H8`) og
`_styresystem/planer/2026-09-19-korthed-med-et-maalt-loft-plan.md` (spor `K1`-`K7`).

### 1. MR !49350 er BLOKERET, og reviewerens besked er en generel rettelse

> ⛔ **AFLØST 20-09-2026: MR'en er merget, og uden 52.** Blokeringen blev ryddet samme aften med
> en rebase (`1899e4c9`), og forløbet står i registeret 2026-09-22 nederst i denne fil.

linsui skrev 2026-09-19 kl. 07:58 UTC, verbatim: »Please take a look at
https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage and rebase the
branch.« og »Don't write so long description. We can't read it.«

Målt samme dag (anonymt – GitLabs API svarer 200 uden token på metadata, 401 kun på noter):
`state=opened`, `detailed_merge_status=conflict`, beskrivelsen **6.193 tegn over 87 linjer**, og
grenen bærer **52 commits** – 49 fra den squash-mergede !40810 plus tre dubletter af
»Husk 1.1 (52)«. **Konflikten er HISTORIK, ikke indhold.** Kuren er et force-push af en gren
genskabt oven på upstream master; se `H1`, og fælden i `_styresystem/infra/gitlab.md`.

### 2. 1.1 indførte TO fejl i den samme funktion, ikke én

Den første – `requestFront`s dobbelte demand-løkke – er rettet på `main` i `1bcd31b` og
**aldrig udgivet**; den ligger i `H3`.

Den anden er ⛔ **en KODELÆSNING, ikke en måling**, fundet af den adversariske verifikator og
efterprøvet på disk: `requestFront` sætter `othersHaveCamera = false` ubetinget
(`CameraService.java` l. 268) og vælger derefter et nyt `targetCamId`, mens
availability-callbacken (l. 283-290) kun latcher for det id der ER `targetCamId` når hændelsen
kommer. Holder en anden app allerede den NYE sides kamera, siger flaget »ledig«, og `demandCheck`
kalder `openCamera` på et optaget kamera. Det ligger i `H3b`, som **måler før den retter**.

### 3. `Rig.useFront` persisteres ikke – et sideskift tabes ved procesgenstart

`Rig.java` l. 39 er en bar `static volatile boolean`, og den sættes kun tre steder:
intent-extraet i `CameraService` l. 105, `/set` i `ControlServer` l. 480, og `requestFront`.
Ingen af dem skriver til `Settings.Global` eller til en preference.

**Det betyder at installationen af 1.2 selv nulstiller valget:** `MY_PACKAGE_REPLACED` genstarter
processen, og kameraet er tilbage på bagsiden uden at nogen rørte `/set`.

⚠️ **Det er IKKE en invariant-fejl, og det bestod ikke nødvendigheds-prøven** – ingen af de to
planers spor kan fejle uden det. Det står her frem for i en plan, fordi det er ægte, udførbart
arbejde på denne flade som ingen har besluttet skal gøres. Kuren ville være den samme kanal som
`husk_token` bruger: `Settings.Global`, som overlever en afinstallation.


## Register 2026-09-22 (`HFs-lenovo`): MR !49350 er MERGET, og 52 nåede aldrig ind i F-Droid

Anledningen er `linsui`s mails i tråd `1a0bdbb11861d4c3` på `hf@brobjerg.dk`.
**Datoerne er artefakternes, ikke sessionens** (måleregel 274/469): merget skete 20-09, og denne nedskrivning 22-09.

**Forløbet, hentet fra GitLab-API'et 2026-09-22.**
⚠️ **Hver række bærer sit kilde-felt**, fordi de to kilder er forskudt: notifikations-mailen om merget har `Date: 07:33:13`, altså to sekunder efter MR'ens `merged_at`.
Her stod først »44 sekunder«, regnet fra en mail-`Date` til et API-felt, hvilket er præcis den blanding måleregel 469 forbyder.

| Tidspunkt (UTC) | Kilde-felt | Hvad |
|---|---|---|
| 18-09 23:52:06 | MR `created_at` | MR !49350 oprettes fra `hf16/f-droid:co.xplat.husk` |
| 19-09 07:58:13 | note `created_at` | `linsui`: rebase grenen efter wiki'ens Git-Usage, og »Don't write so long description. We can't read it.« (allerede måleregel 476) |
| 19-09 21:22:32 | system-note | vi rebaser oven på `fdroid:master`, så MR'en bliver én commit der kun rører `metadata/co.xplat.husk.yml` |
| 19-09 21:40:49 | note `created_at` | vores svar: »Thanks. Rebased onto master ...«, altså begge anmodninger efterkommet |
| 19-09 21:46:37 | system-note | commiten `1899e4c9` »Update Husk (co.xplat.husk) to 1.2 (53)« lægges på |
| 20-09 07:32:24.5 | note `created_at` | `linsui` lægger en `suggestion:-6+0` på 1.1-entryen, altså en ren SLETNING af de seks linjer |
| 20-09 07:32:44 | system-note | alle tråde markeres løst af `linsui` selv |
| 20-09 07:32:51 | system-note | `linsui` pusher `9961872f` »Apply 1 suggestion(s) to 1 file(s)« |
| 20-09 07:33:11.2 | MR `merged_at` | **MR !49350 er merget** af `linsui` som squash-commit `60e114ae` ind i `fdroid/fdroiddata:master` |

Fra forslaget til merget gik der **46,7 sekunder**, så forslaget blev hverken begrundet eller afventet.
Anmelderen rettede altså selv frem for at bede om en ny runde.
Det er n=1 og ikke en regel om F-Droid, men formen er værd at kende: en entry kan forsvinde ud af en MR uden en kommentar der forklarer det, og uden at vi når at se den.

**Hvad der faktisk står i upstream nu (målt 2026-09-22 mod den rå fil på `master`):**
`Builds:` bærer **kun 1.0/51 og 1.2/53**, og `CurrentVersion: '1.2'` / `CurrentVersionCode: 53`.
1.1/52 findes ikke, og bliver ikke bygget medmindre en senere MR lægger entryen ind igen: F-Droid bygger kun det der står i recipen.

**Reproducerbarheden er målt FØR fjernelsen, og den holdt for begge.**
Fork-jobbet `16607081166` (»fdroid build«, ref `co.xplat.husk`) kørte på `1899e4c9`, altså på den udgave der stadig bar BÅDE 52 og 53, og endte `success` 19-09 21:50:47Z.
Trace'en siger `supplied reference binary has allowed signer 96195cfd540e75f8a34dfc08764438769d4bc3e5d7970a9527d97004d4f2c17d` og producerer reproducerbarheds-artefakter for begge: `co.xplat.husk_52.binary.apk.json` og `_53.binary.apk.json`.
Det er dermed ikke en fejlet reproduktion der kostede 52 sin plads.

**F-Droid har PUBLICERET 53** (målt 2026-09-22):
- `https://f-droid.org/api/v1/packages/co.xplat.husk` → `suggestedVersionCode: 53`, og `packages` er 53 + 51.
- `co.xplat.husk_53.apk` → HTTP 200, `co.xplat.husk_51.apk` → 200, `co.xplat.husk_52.apk` → **404**.
- Den publicerede APK er **sha256-identisk med GitHub-releasens**: `54b5e2d00f5e9d4cdcc052a2948e4f74d76c359cfe414eb1c1b3ba7707b30158`, begge 86.738 bytes.
  Det er `Binaries:`-vejen der virker efter hensigten: F-Droid serverer vores eget signerede binære, ikke sit eget genbyg.
  ⛔ **Størrelsen alene kan ikke bruges som tjek** – `v1.1` og `v1.2` har SAMME bytestørrelse, så sammenlign altid sha256.

**Hvad runden rettede her:**
1. `fdroid/co.xplat.husk.yml` bar stadig 52-entryen og var dermed drevet fra det der blev merget.
   Den er nu bragt i overensstemmelse og verificeret med `diff` mod den rå upstream-fil: **identisk**.
   Havde den fået lov at stå, ville næste opdaterings-MR have genindført præcis de seks linjer `linsui` fjernede.
2. `CLAUDE.md` trin 4 og `docs/BUILD.md` afsnit 7 sagde begge at »den levende MR er `!49350`«.
   Det er falsk siden 20-09, og formuleringen er den samme fælde som `!40810` var før 15-09: en merget MR der læses som en åben kanal.
   **Der findes ingen levende MR nu**, og næste release kræver en NY fra en gren der er frisk fra upstream master.

**Hvorfor 52 blev fjernet, står ingen steder, og det ER målt.**
Med husets GitLab-token (`bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt'`) svarer `/merge_requests/49350/notes` **HTTP 200 med 11 notes**, og `linsui`s eneste to menneskeskrevne er rebase-beskeden 19-09 og selve forslaget, hvis body er `suggestion:-6+0` og intet andet.
Der findes altså ingen begrundelse at læse, hverken i MR'en eller i mailene.
Den nærliggende læsning er at en allerede overhalet version ikke er værd at bygge, men det er en formodning, ikke en måling.
⛔ **Her stod først at endpointet »svarer 401 Unauthorized uden token«.** Det er sandt og irrelevant: huset HAR tokenet, og `docs/BUILD.md` afsnit 7 citerer selv kommandoen der henter det.
En adgang jeg selv kunne have skaffet, er ikke en umålelighed - den er en måling jeg ikke tog.

# Arkiveret fra FORTSÆT-HER.md 2026-09-23 (1.3 udgivet), ordret

## ✅ 2026-09-19: 1.2 ER UDGIVET

Kørt på `hfs-dell` efter `_styresystem/planer/2026-09-19-husk-udgiv-loekkefix-plan.md`.
**Datoen er artefakternes, ikke sessionens** (måleregel 274/469): byggecommiten `390d9c5`
bærer `2026-09-19 23:33 +0200`, og GitHub-releasens `published_at` er `2026-09-19T21:37:20Z`.
Selve lukningen løb ind i den 20., og her stod først den dato.

| Hvad | Tilstand |
|---|---|
| Version | **1.2 / versionCode 53**, tagget på byggecommiten `390d9c5e` |
| Hvad 1.2 retter | `CameraService.requestFront` kaldte `h.post(demandCheck)`, og `demandCheck` genplanlægger sig selv med `postDelayed(this, 1000)`. N sideskift gav N+1 samtidige 1-sekunds-løkker, i strid med invariant C. Kuren er ét kodested, `planlaegDemandCheck()`, som fjerner ventende kald før den poster. Fejlen sad i den UDGIVNE 1.1-APK. |
| Hvad 1.2 IKKE ændrer | `ControlServer.java` er byte-uændret fra `v1.1`, så ingen nye endpoints, params, respons eller adgangsmodel. `AndroidManifest.xml` og `MainActivity.java` ændrer kun kommentarer. Tilladelses-listen er identisk: **16 mod 16** `uses-permission`. ⚠️ Her stod »17 mod 17«, målt med `grep -c permission`, som også tæller `android:permission=` på servicen. Tæl `uses-permission`. |
| Signatur | `96195cfd…c17d`. Den HENTEDE fil fra GitHub er sha256-identisk med den signerede. |
| xplat.co | deployet; `https://xplat.co/husk/latest.json` viser 53, `check-api-parity.sh` grøn (43 endpoints). |
| F-Droid | ✅ **MR !49350 er MERGET 2026-09-20 kl. 07:33:11Z** af `linsui` (squash-commit `60e114ae`, head `9961872f`). ⚠️ **52 nåede ALDRIG ind:** `linsui` lagde en `suggestion:-6+0` på 1.1-entryen og merged derefter, så upstream master bærer kun 51 og 53. F-Droid PUBLICERER 53 (målt 2026-09-22): `/api/v1/packages/co.xplat.husk` giver `suggestedVersionCode: 53`; `co.xplat.husk_53.apk` → 200, `_52.apk` → **404**. Den publicerede APK er sha256-identisk med GitHub-releasens (`54b5e2d0…0158`). **Der er dermed INGEN levende MR** – næste release kræver en NY fra en gren frisk fra upstream master. |
| Flåden | ⚠️ **ingen enhed er på 1.2.** Kun spare SM-A102U1 (.101.102) er på 1.1; Note10 og Sony 702SO er på 1.0. |

**Recipen er synkroniseret 2026-09-22:** `fdroid/co.xplat.husk.yml` bar stadig 52-entryen, og en ny MR oven på den ville have genindført præcis det `linsui` fjernede.
Den er nu diff-identisk med upstream master; forløbet står i `docs/versionshistorik.md`.

### ⛔ To ting der kræver et menneske, og som IKKE er gjort

1. **Note10 og A9 er ikke opgraderet, og det var et VALG.** Note10 er kontor-mødekameraet: en
   opdatering dræber app-processen, og DeX-rig'en har historik for at tabe a11y, scrcpy og
   Discord. A9 (Sony 702SO) kan afbinde a11y ved en opdatering og har **ingen Wireless
   Debugging**, så en fejl kræver et USB-kabel på stedet. Planlæg dem fysisk, ikke remote.
2. **`latest.json` kan først slettes når HELE flåden er på 1.1 eller derover.** Målingen der
   frigiver den: `/info` viser 52 eller derover på hver enhed. Se tabellen i `CLAUDE.md`.

## Adversarisk verifikation (`/luk-runde` Trin 3, 2026-09-19, `hfs-dell`)

Frisk sub-agent (`fable`) over rundens diff, gate-definitionerne ordret, og uden orkestratorens
konklusioner. Planen `2026-09-19-husk-udgiv-loekkefix-plan.md` retireres, så dommene står her.
**Dertil tre gennemgange af udadvendt tekst før afsendelse** (MR-beskrivelse, MR-kommentar,
butikstekst), som hører til den gate runden selv indførte.

| Linse | Dom | Målt |
|---|---|---|
| Planens 8 lukke-betingelser | **nej** | Alle mod artefakter. Ekstra: `v1.1`- og `v1.2`-APK'en har SAMME bytestørrelse (86.738), forskellig sha256. Et størrelses-tjek kan ikke skelne dem. |
| Gate 1-3 (modul/template) | **nej** | `udadvendt.py` er en CLI-gate, ikke et webmodul. Sidefund: `doem_adversarisk` findes nu i `gmail.py` OG her. |
| Gate 4 (læring routet) | **ja** | `CLAUDE.md` pegede på »måleregel 474«, som ikke fandtes. Nu skrevet. |
| Gate 5 (markør) | ikke rel. | En release efter release-pligten er rutine; rutine stempler ikke. |
| Gate 7 (handoff/docs) | **ja** | Fire: »intet slettet« var falsk (linje 104-132 + 478-520 af 520 hverken arkiveret eller båret med); `!40810` stod tre steder som levende MR; »17 mod 17« var 16; datoen 09-20 modsagde `390d9c5` (09-19 23:33). Alle rettet. |
| Gate 8 (PII) | **nej** | Ingen ny persondata, auth eller angrebsflade. Sikkerhedspåstandene efterprøvet mod `Net.peerAllowed`. |
| Gate 9 (blast-radius) | **delvist** | `infra/enheder.md` pegede på et flyttet afsnit; rettet. Forbrugerne upåvirkede: `ControlServer.java` byte-uændret. |
| Gate 11 (infra) | **delvist** | Fire uskrevne kapabiliteter: GitLabs `force`-commit, `merge_ref`-genberegningen, `PUT` på fremmed MR, og `gh`-tokenets ugyldighed mod `git credential fill`. Alle skrevet. |
| Testsuiten | **ja** | Suiten var ikke selvstændig: 21 af 28 uden for porteføljen, fordi den læste husets rigtige `konstanter.tsv`. Og titlen var ugatet i begge værktøjer. Nu 31 af 31 begge steder. |
| Udadvendt tekst (3 gennemgange) | **ja** | MR-teksten bar 2 fejl, butiksteksten 10. Ni af de ti var et FORBEHOLD hængt på en term der overlevede snittet - se måleregel 475. |

⚠️ **Ikke rettet:** `docs/versionshistorik.md` er nu 38 KB og vokser med hver nedskæring. Den
auto-loades ikke og har derfor intet loft i dag.
