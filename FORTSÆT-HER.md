# P_app_husk – fortsæt her

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver xplat): den publicerede
FOSS-app der gør en gammel Android-telefon til fjernstyret kamera plus
accessibility-automationsmotor plus scrcpy/adb-bro over eget Tailscale-net, uden
root. Overblik: `README.md`. Agent-kontekst, invarianter og release-pligten:
`CLAUDE.md` – **læs release-blokken øverst i den før du rører app-koden**.

## Status

**Udgivet og i drift.** Nuværende version **1.0 / versionCode 51** (2026-09-07, bygget og
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



## F-Droid MR !40810: 1.0 er indsendt 07-09-2026, bolden ligger hos F-Droid

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
- **Ingen fejlsignal når »Installer ukendte apps« er slået fra.** `Updater` bør kalde
  `canRequestPackageInstalls()` FØR den henter, og sætte fx `ERR unknown-sources-off` i
  `lastUpdate`. I dag står feltet på »install requested« for evigt, og `/flags` ser sund ud.
- **`ControlServer.java:225`: `dparam(query,"server") != null`.** `dparam` returnerer `""` og
  aldrig `null`, så guarden er ALTID sand - præcis 0.9.31's `topic`-fejl. I dag ufarlig, fordi
  https-præfikstesten redder den, men én refaktor fra at bide.

> ✅ **Udkom i 1.0:** `Net.tailscaleIp()`s carrier-CGNAT-mislabel og de danske strenge i
> HTTP-svar. Stod her som kø-punkter indtil 2026-09-08.

## Åbne spor fra runde-planer

<!-- SPOR-POINTERE: genereret af check-plan-pointers.sh - rediger ikke her -->
- [ ] SPOR: `2026-08-19-infra-docs-d04-plan.md` margen-S2 – Synk-gaten standser fem portefølje-tjek, fordi tre repoer er bagud
- [ ] SPOR: `2026-08-19-infra-docs-d06-plan.md` HF6 – Bring `gradle-build.sh` tilbage til ren ASCII
- [ ] SPOR: `2026-08-19-infra-docs-d07-plan.md` HF8 – Efterprøv F-Droids publicerede beskrivelse efter merge
- [ ] SPOR: `2026-09-07-husk-fdroid-restfund-plan.md` S1 – Afpublicér de gamle GitHub-releases (B1)
- [ ] SPOR: `2026-09-07-husk-fdroid-restfund-plan.md` S2 – Sæt et token på de to spares (B3)
- [ ] SPOR: `2026-09-07-husk-fdroid-restfund-plan.md` S4 – `peerAllowed` og obligatorisk token: design og forelæg
- [ ] SPOR: `2026-09-07-husk-fdroid-restfund-plan.md` S8 – Næste Husk-release: tre målte kode-fund
- [ ] SPOR: `2026-09-07-husk-fdroid-restfund-plan.md` S9 – Hvorfor kom kamera- og skærmtjenesten ikke op efter opdateringen?
<!-- /SPOR-POINTERE -->
