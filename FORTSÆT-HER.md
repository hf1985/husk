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

| Hvad | Tilstand |
|---|---|
| Version | **1.4 / versionCode 55**, tag `v1.4` på byggecommiten `237b7cb`, bygget fra `git archive HEAD`. Kode i `4a0c345` |
| Hvad 1.4 gør | Token-felt i appen (Generér/Kopiér/Gem), `/token/request` + `/token/status` med Godkend på telefonen, `/token/set`. Prefs er eneste kilde; den globale systemindstilling læses ikke (ingen migrering) |
| Signatur | `96195cfd…c17d`; asset hentet fra GitHub er sha256-identisk (`83a1ddf7…cfa3`). 16 `uses-permission` som i 1.3 |
| xplat.co | deployet (`P_xplat` `0563aef`); `latest.json` viser 55, APK 200; `check-api-parity.sh` grøn (46 endpoints) |
| Repoets `latest.json` | slettet; `CLAUDE.md` og `docs/BUILD.md` nævner den ikke mere |
| F-Droid | **MR `!50000`** fra en gren genskabt oven på upstream master. Forkens pipeline `2880318030` grøn: F-Droid byggede 55 og verificerede mod vores binær. Ikke merget endnu |

⚠️ `check-api-parity.sh` så indtil 1.4 ikke ruter med to segmenter (tegnklassen manglede `/`); rettet.
⚠️ `git grep -c '"/token/'` fra Git Bash taber det literale `"` på vej til `git.exe` og svarer 0; `grep -c` på filen svarer 3.

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
- **Sony 702SO** `.101.101`: **1.3 / 54**, tokenløs, ingen Wireless Debugging (Android 9). Når den har 1.4:
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

## Adversarisk verifikation (`/luk-runde` Trin 3, 2026-09-24, `HFs-lenovo`)

Ad hoc-runde (flåde-ajourføring, token på A10e, 1.4-planen). Frisk `fable`-agent over diffen og PLAN.md 4A. Runden 2026-09-23s tabel ligger i git (`e650ff3`).

| Linse | REFUTERET | Målt og gjort |
|---|---|---|
| Handoff mod `CLAUDE.md` | delvist | `latest.json` modent uden vej; nu planens `S6` |
| Planen køreklar | delvist | forkert webcam-HEAD (`da98525`), `P_xplat` manglede i feltet, S9 uden WD-gendannelse, S4 for smal lukning, værn 1 falsk på tokenløs enhed, A10e manglede i ingen-migrering. Alle rettet |
| Gate 4 | ja | tre urutede læringer; skrevet i `_styresystem/laering/2026-09-24-adgang-maalt-fra-en-maskine.md` |
| Gate 7 | ja | `infra/enheder.md` og husk-webcams handoff var bagud; rettet |
| Gate 8 | delvist | ingen secrets i diffen; den nye auth-flade er beskrevet i planen |
| Gate 9, 11 | delvist | token rammer husk-webcam og `pc/spare.sh`; adb-parringen fra dell skrevet i `enheder.md` |

**Trin 4 (baseline 120 poster):** NYE FUND 0 (cache `e2c76b7e7985` mod baselinen). UKENDT 8: seks har korpus `ingen`/intet felt og kan ikke stå i en baseline; `check-eol-vs-attributes.sh` og `check-trae-tilbagerulning.sh#1` kørt enkeltvis: fund i Kärnfull-repoer og uberørte webcam-scripts, intet i rundens filer. BAGGRUND 26, ingen i rundens korpus.

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
