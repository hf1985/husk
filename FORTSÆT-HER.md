# P_app_husk – fortsæt her

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver xplat): den publicerede FOSS-app der gør en
gammel Android-telefon til fjernstyret kamera plus accessibility-automationsmotor plus
scrcpy/adb-bro over eget Tailscale-net, uden root. Overblik: `README.md`. Agent-kontekst,
invarianter og release-pligten: `CLAUDE.md` – **læs release-blokken øverst i den før du rører
app-koden**.

> **Denne fil er skåret ned 2026-09-19** fra 36.204 bytes efter ejerbeslutningen »Gate nyt, ryd
> kun det der læses udefra eller ved hver sessionsstart«. Loftet er 12.000 bytes, fordi filen
> auto-loades i hver agents kontekst. Historikken er flyttet ORDRET til
> `docs/versionshistorik.md` (»Arkiveret fra FORTSÆT-HER.md 2026-09-20«, to dele), og de tre
> BESLUTTEDE opgavers opskrifter til `docs/besluttede-opgaver.md`.
> ⚠️ Her stod »intet er slettet«. **Det var falsk:** første flytning tog linje 133-477 af en fil
> på 520, og tre ting faldt ud i omskrivningen af resten. Den adversariske verifikation fandt det,
> og »anden del« i historikfilen bærer dem nu ordret.
> **2026-09-23:** 1.2-afsnittet og verifikationen af 2026-09-19 er flyttet ordret til
> `docs/versionshistorik.md` (»Arkiveret fra FORTSÆT-HER.md 2026-09-23«). De to kodefund og
> afleveringen om `pc/udadvendt.py` er slettet her: de to fund og `MIDLERTIDIGE` er udført
> (`e5bacb9`, `757f5f7`). Afleveringens »beslægtede fund« (to læsere af `konstanter.tsv`) er IKKE
> udført; det står som fund 2 i `_styresystem/FORTSÆT-HER.md` 2026-09-20.

## Åbne småting fra runde-luk 2026-09-23

- **`docs/versionshistorik.md` har 247 bytes tilbage** til `projekt-historik-loft` (50000).
  Næste flytning af historik dertil gør den rød; retirér da ældre historik til et arkiv.
- `pc/udadvendt.py`: `laes_loft`s `ud`-parameter bruges ikke længere efter `757f5f7`. Kosmetisk.
- `pc/fdroid-fork-update.py` genskaber IKKE forkgrenen oven på upstream master; efter en merget MR
  skal det gøres først (1.4: et commits-API-kald med `force`, `start_project` 36528). Kan foldes ind.

## ✅ 2026-09-24: 1.4 ER UDGIVET (`HFs_Dell`)

Genmåling før start afveg kun ved handoff-commits: Husk `4fca1b9` (planen: `31a0575`), husk-webcam `ca86e2b`
(planen: `da98525`); `P_xplat` `409780a` som planen, efter at det maskin-lokale indeks var fremført (måleregel 403).

| Hvad | Tilstand |
|---|---|
| Version | **1.4 / versionCode 55**, tag `v1.4` på byggecommiten `237b7cb`, bygget fra `git archive HEAD`. Kode i `4a0c345` |
| Hvad 1.4 gør | Token-felt i appen (Generér/Kopiér/Gem), `/token/request` + `/token/status` med Godkend på telefonen, `/token/set`. Prefs er eneste kilde; den globale systemindstilling læses ikke (ingen migrering) |
| Signatur | `96195cfd…c17d`; asset hentet fra GitHub er sha256-identisk (`83a1ddf7…cfa3`). 16 `uses-permission` som i 1.3 |
| xplat.co | deployet (`P_xplat` `0563aef`); `latest.json` viser 55, APK 200; `check-api-parity.sh` grøn (46 endpoints) |
| Repoets `latest.json` | slettet; `CLAUDE.md` og `docs/BUILD.md` nævner den ikke mere |
| F-Droid | **MR `!50000`** fra en gren genskabt oven på upstream master. Forkens pipeline `2880318030` grøn: F-Droid byggede 55 og verificerede mod vores binær. Ikke merget endnu |

⚠️ `check-api-parity.sh` så indtil 1.4 ikke ruter med to segmenter (tegnklassen manglede `/`); rettet.
⚠️ `git grep -c '"/token/'` fra Git Bash svarer 0, fordi MSYS sti-konverterer et argument med `/segment/` (måleregel 452-klassen), ikke fordi `"` tabes: `MSYS_NO_PATHCONV=1 git grep ...` svarer 3.
⚠️ **Kendt fejl til næste release (fundet af Trin 3, ikke rettet i 1.4):** `TokenRequests.decide` fornyer ikke `createdMs` ved Godkend, så en godkendelse tæt på 120 s kan nå at udløbe før klienten henter; på en tokenløs enhed er tokenet da SAT uden at nogen fik det (læs det i appens felt). Kur: sæt `createdMs = now` ved Godkend.

1.3 (2026-09-23, `4c42206`, MR `!49892` merget): kameraside huskes i prefs. Detaljer: `git show e650ff3`.

## ⛔ Flåden 2026-09-24: Note10 på 1.4 med token; A10e NEDE; 702SO tokenløs

- **Note10** `.103.102`: **1.4 / 55**, opdateret headless 2026-09-24 fra `HFs_Dell` (Termux-ADB:
  `/wd`, `adb connect 127.0.0.1:15557`, `push` + `pm install -r`; ingen genstart, `MainActivity` ikke startet).
  Tokenet sat igen med `/token/request?client=vagt&new=<husk-rig-token>` + Godkend; udleveret token =
  `husk-rig-token`, id'et svarede `expired` bagefter. Målt efter: `/info` 401 uden og med forkert token,
  200 med og viser 55; `/snapshot` 200 JPEG (to gange); `/screen.jpg` 503 »no screen frame« som FØR
  opdateringen. Også målt: Afvis giver `denied` og derefter `expired`, en anden anmodning imens giver
  429, `/token/set` uden token 401 og med ugyldigt `new` 400. Ikke målt: 409 og 503 (notifikationer fra).
  ⚠️ **Godkend via a11y virker ikke direkte:** notifikationen er sammenfoldet, og `find Approve` rammer
  brødteksten (»…Approve to set one…«), ikke knappen; klik gav intet. Det der virkede: Termux-ADB
  `cmd statusbar expand-notifications`, swipe ned på notifikationen, `uiautomator dump`, `input tap`
  på knappen (`text="Approve"`). Telefonens UI er engelsk.
- **SM-A102U1 (A10e)** `.101.102`: var **1.3 / 54**. ⛔ **NEDE siden `adb reboot` 2026-09-24** - kræver
  ejeren ved telefonen. Når den er oppe og har fået 1.4 (F-Droid kan selv opdatere den): sæt tokenet i
  appens felt med værdien fra vault-itemet `Husk token SM-A102U1`. Den gamle adb-sætning læses ikke af 1.4.
- **Sony 702SO** `.101.101`: **1.3 / 54**, tokenløs, ingen Wireless Debugging (Android 9). Vejen til 1.4 er
  F-Droid-klienten hvis den er installeret, ellers `adb install -r` med kabel på stedet. Når den har 1.4:
  klik »Hent fra telefonen« i husk-webcam, godkend på telefonen, og læg tokenet i vaulten som
  `Husk token 702SO` (`put-login`).

⛔ **Rettelse af handoff-commit `1e7aa0e`:** »headless adb findes ikke« og »/screen.jpg
svarer No screen frame, så UI'et kan ikke ses« var FORKERT. `/control` (H.264) viser skærmen på begge
spares, og A10e er parret med `hf198@HFS_DELL`, så `adb connect 100.100.101.102:15557` lykkes fra dell
(ikke fra lenovo, hvis nøgle ikke er parret). `/screen.jpg` er MJPEG-vejen og var tom, mens H.264-vejen virkede.

⛔ **`adb reboot` på en spare kan tage den helt af nettet til nogen låser den op.** `CLAUDE.md`s
deploy-opskrift (»`adb install -r`, derefter `adb reboot`«) er skrevet til Note10-riggen; brug den ikke
på en spare uden at nogen er ved telefonen.

Ejerens svar om 1.4 (felt, API, ingen migrering) står verbatim i planen `2026-09-24-husk-token-i-appen-plan.md`,
som efter runde-luk kun findes i `10_PROJEKTER`-repoets historik (`git log --all -- '*husk-token-i-appen*'`). Genforeslå dem ikke.

## BESLUTTEDE opgaver: status 2026-09-24

Opskrifterne står i `docs/besluttede-opgaver.md`.

1. **Afpublicér APK-assets til og med `v0.9.30` – ✅ UDFØRT 2026-09-23** efter ejerens ja.
   42 assets slettet (releases og tags står); listen og værnene: `docs/afpubliceret-2026-09-23.txt`.
   Eftermålt: tre stikprøver 404, `v0.9.31`/`v1.0`/`v1.1`/`v1.2`/`v1.3` alle 200.
2. **Token på spares – ÅBEN:** 1.4 er udgivet; A10e er nede og 702SO står på 1.3 uden token. Trinene pr. enhed står i flåde-afsnittet.
3. **503-årsagen fra 2026-09-07 – AFSKREVET af ejeren 2026-09-23** (»Den er overflødig«). Genrejs den ikke.

## Adversarisk verifikation (`/luk-runde` Trin 3, 2026-09-24, `HFs_Dell`)

Planen `husk-token-i-appen` (1.4). Frisk `fable`-agent over de tre repoers diff og PLAN.md 4A. Forrige rundes tabel: `git show f2fadd2:FORTSÆT-HER.md`.

| Linse | REFUTERET | Målt og gjort |
|---|---|---|
| S1, S3, S5-S10 | nej | artefakter målt: tags, live `latest.json` 55, MR `!50000`, suite 217, Note10 401/200 |
| S2, S4 | delvist | kodens krav holder; plan-grep `husk_token` rammer kanal-id'et `husk_token_request` (delstreng). Statuslinjerne omformuleret |
| Gate 4 | delvist | `git grep`-fejlen var MSYS-sti-konvertering, ikke tabt `"`; rettet ovenfor. Læring skrevet i `_styresystem/laering/` |
| Gate 7 | delvist | `docs/fleet-tailnet-transport.md` og `docs/besluttede-opgaver.md` forældede; 702SO's vej til 1.4 manglede. Rettet |
| Gate 8 | delvist | ingen secrets; TTL-kant i `TokenRequests.decide` (kendt fejl ovenfor); README siger nu at en peer kan sætte sit eget token på en tokenløs enhed |
| Gate 9 | nej | `pc/spare.*`, `P_kontor`, `P_add-on_phone-transport` bærer ingen adb-token-vej |
| Gate 11 | ja | `infra/enheder.md` sagde 1.3/54; rettet (kun rundens egen linje committet) |
| `P_xplat` | ja | to manglende mellemrum i API-teksten; rettet, deployet, live-målt (`a9c3d4c`) |

⚠️ **Forrige runde (`HFs-lenovo`) efterlod governance-ændringer UCOMMITTEDE på Drive:** `infra/enheder.md`s
A10e-/genmålings-linjer og `laering/2026-09-24-adgang-maalt-fra-en-maskine.md` findes ikke på `origin`.
Denne runde har ikke committet dem (fremmed arbejde, måleregel 60/163).

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
