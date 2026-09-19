# P_app_husk – fortsæt her

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver xplat): den publicerede
FOSS-app der gør en gammel Android-telefon til fjernstyret kamera plus
accessibility-automationsmotor plus scrcpy/adb-bro over eget Tailscale-net, uden
root. Overblik: `README.md`. Agent-kontekst, invarianter og release-pligten:
`CLAUDE.md` – **læs release-blokken øverst i den før du rører app-koden**.

## ✅ 2026-09-19: 1.1 ER UDGIVET, og F-Droid-fundene er lukket

Kørt på `HFs_Dell` efter `_styresystem/planer/2026-09-18-husk-webcam-produkt-plan.md`,
sporene H1-H7. **Afsnittene nedenfor om de ni åbne fund og om MR !40810 er HISTORIK.**

| Hvad | Tilstand |
|---|---|
| Version | **1.1 / versionCode 52**, tagget på byggecommiten `eddaa23` |
| Signatur | `96195cfd...c17d`, verificeret på den HENTEDE fil fra GitHub |
| De ni fejl | lukket i 1.1. Fund 1 er dog stadig åbent for **1.0-entryen** - se MR'en |
| Advarsel 10 + spørgsmål 11-15 | besvaret i butiksteksten, vært for vært |
| F-Droid | **MR !49350** åbnet mod fdroid/fdroiddata. Fork-pipelinen var grøn, og BEGGE entries reproducerede mod referencebinæren med den tilladte signer. ⛔ **MEN MR'en er BLOKERET siden 2026-09-19 – se registret nederst** |
| Flåden | ⚠️ **kun spare SM-A102U1 (.101.102) er på 1.1.** Note10 og Sony 702SO er stadig på 1.0 |

### ⛔ To ting der kræver et menneske, og som IKKE er gjort

1. **Note10 og A9 er ikke opgraderet, og det var et VALG.** Note10 er kontor-mødekameraet: en
   opdatering dræber app-processen, og DeX-rig'en har historik for at tabe a11y, scrcpy og
   Discord. A9 (Sony 702SO) kan afbinde a11y ved en opdatering og har **ingen Wireless
   Debugging**, så en fejl kræver et USB-kabel på stedet. Planlæg dem fysisk, ikke remote.
2. **`latest.json` kan først slettes når HELE flåden er på 1.1.** Målingen der frigiver den:
   `/info` viser 52 eller derover på hver enhed. Se tabellen i `CLAUDE.md`.

## ⛔ Tre BESLUTTEDE opgaver der mistede deres eneste levende peger 2026-09-19

Disse tre stod som `SPOR:`-punkter i en genereret blok øverst i denne fil. Blokken blev fjernet
af `check-plan-pointers.sh` i commit `4cb5b24`, fordi generatoren ikke længere kunne se deres
plan: `2026-09-07-husk-fdroid-restfund-plan.md` blev arkiveret med hele plankøringssystemet til
`_arkiv/2026-09-13-plankoeringssystemet/planer/lukkeplaner/`. **Generatoren regenererer dem
derfor aldrig.** De er skrevet ud i fuld form her, fordi en peger til en arkiveret plan ikke er
en udgang. Fundet af den adversariske verifikator 2026-09-19.

**Alle tre er BESLUTTET** (B1 og B3 i den arkiverede plan); det der mangler, er udførelsen.

### 1. Afpublicér APK-assets til og med `v0.9.30` (var `S1`) - SIKKERHED

Kodeordet til den pensionerede signeringsnøgle (`CN=Debug, O=KHFRB`, alias `ad`, SHA-256
`1b89a920...62af59`) kan hentes fra det OFFENTLIGE repos git-historik. Keystore-filen selv har
aldrig været committet, men **alle releases til og med `v0.9.30` er signeret med den nøgle og er
stadig downloadbare**, så enhver kan signere en APK der ser gyldig ud for en gammel installation.

⚠️ **Ikke udført, og bevidst ikke udført af en uovervåget session:** det er en irreversibel
bulk-sletning af offentlige artefakter. Beslutningen er truffet; udførelsen kræver et menneske
der siger ja, og en session der måler FØRST.

1. Opregn før du sletter: `gh release list --repo hf1985/husk --limit 60`, og pr. release
   `gh release view <tag> --repo hf1985/husk --json assets -q '.assets[].name'`. Skriv listen
   til en fil og TÆL den. Forventet: 28 releases `v0.9.1`-`v0.9.30`.
2. **Fjern APK-ASSETTET, ikke release-noten** - assettet bærer angrebsfladen, noten er historik:
   `gh release delete-asset <tag> <asset-navn> --repo hf1985/husk --yes`.
3. Skriv én linje i hver berørt note om hvorfor assettet er væk (`gh release edit <tag> --notes-file <fil>`).
4. ⛔ **Rør ALDRIG `v0.9.31`** - den er signeret med den nye nøgle, og F-Droids `Binaries:` henter
   `husk-v0.9.31.apk` derfra. Sletter du det asset, brækker du indsendelsen.
5. Ryd modsigelsen i `docs/BUILD.md` (~l. 438-440 mod ~l. 247-253) om hvor længe debug-keystoren
   skal blive. Flåden ER geninstalleret. Slet restkopier med
   `bash _styresystem/scripts/safe-delete.sh --shred <sti>`.

**Verifikation:** `v0.9.30` viser ingen APK-assets, `v0.9.31` viser stadig sin, og
`curl -sI .../v0.9.31/husk-v0.9.31.apk` svarer 302.

### 2. Sæt et token på de to spares (var `S2`) - SIKKERHED

Tokenet er tomt som standard, så kilde-IP-ACL'en er eneste spærre - og den lukker hele
`100.64.0.0/10` ind, ikke kun vores eget tailnet. **Det står nu offentligt i MR-tråden.**
Genmålt 2026-09-19: `/info` og `/flags` svarer stadig 200 uden token på begge spares.

1. Generér ét token pr. enhed (mindst 24 tegn, alfanumerisk - `ControlServer.sanitizeToken`
   stripper alt andet).
2. Gem dem i vaulten som login-items **FØR** du sætter dem:
   `bash Tools/vault2/vault2.sh put-login "Husk token 702SO" husk <fil>` (og for `SM-A102U1`).
   Aldrig i en fil i repoet.
3. `adb -s <serial> shell settings put global husk_token '<token>'` - værdien bor i
   `Settings.Global` og **overlever en afinstallation**.
4. Læs tilbage, og efterprøv **begge retninger**: `/info` skal svare **401 uden** token og 200
   med. Den negative probe er hele pointen (måleregel 1).
5. Ret `pc/spare.sh` og `pc/spare.ps1` så de sender tokenet. Søg efter flere forbrugere med
   `grep -rn "8090" P_app_husk/pc P_kontor` før du erklærer dig færdig.

### 3. Hvorfor kom kamera- og skærmtjenesten ikke op efter opdateringen? (var `S9`)

MÅLT 2026-09-07: efter in-app-opdateringen til 1.0 svarede `/snapshot` 503 på BEGGE spares (også
på andet kald), og `/screen.jpg` var uden frame på SM-A102U1. Porten var oppe hele tiden, så
flåden så sund ud udefra. Et tap på »Camera streaming« kurerede kameraet.

⛔ **Årsagen er IKKE målt.** Den første forklaring (»ScreenService hostede 8090 alene«) er en
hypotese der er trukket tilbage: `BootReceiver` starter `CameraService` ubetinget ved
`MY_PACKAGE_REPLACED`. Og fordelingen taler imod den: det ramte A9 og A11, ikke A12.

⚠️ **Reproduktionen fra den arkiverede plan kan ikke længere køres som skrevet.** Trin 1 var
»opdatér en spare in-app«, og `/update` er FJERNET i Husk 1.1 (F-Droid-fund 3-9). Tilstanden må
nu fremprovokeres med en almindelig `adb install -r` eller en F-Droid-opdatering. Mål derefter
med `adb shell dumpsys activity services co.xplat.husk` og `adb logcat -d` umiddelbart efter,
og afgør om det er Android 12+'s baggrunds-FGS-restriktion eller noget andet. Resultatet hører i
`docs/fleet-tailnet-transport.md` §5, som i dag påstår at J4-self-healen er komplet.

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
- ✅ **BORTFALDET i 1.1:** »ingen fejlsignal når Installer ukendte apps er slået fra«.
  Hele updateren er fjernet, så tilstanden kan ikke opstå.
- ✅ **RETTET i 1.1:** `dparam(query,"server") != null` var altid sand, fordi `dparam` giver
  `""` og aldrig `null` - præcis 0.9.31's `topic`-fejl. Genmålt 2026-09-19, stadig til stede,
  og skrevet om til `param(...)`.

> ✅ **Udkom i 1.0:** `Net.tailscaleIp()`s carrier-CGNAT-mislabel og de danske strenge i
> HTTP-svar. Stod her som kø-punkter indtil 2026-09-08.



## 📜 HISTORIK 2026-09-18: MR'en er MERGET, men F-Droids review-kit melder ni fejl

> ✅ **LUKKET 2026-09-19 i 1.1.** Alt herunder er bevaret som historik. De ni fejl er rettet,
> MR !49350 er åbnet, og status står i afsnittet øverst. Læs det FØRST - dette afsnit
> beskriver en tilstand der ikke findes mere.

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

## Register 2026-09-19 (ad hoc-runde `husk-loekkefix-og-korthed`, `HFs-lenovo`): MR'en er blokeret, og 1.1 bar TO fejl

**Arbejdet er lagt i to planer, ikke her:**
`_styresystem/planer/2026-09-19-husk-udgiv-loekkefix-plan.md` (spor `H1`-`H8`) og
`_styresystem/planer/2026-09-19-korthed-med-et-maalt-loft-plan.md` (spor `K1`-`K7`).

### 1. MR !49350 er BLOKERET, og reviewerens besked er en generel rettelse

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
