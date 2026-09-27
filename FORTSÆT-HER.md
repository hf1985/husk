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

## 2026-09-25: PC-companionen kører med 1.4 (`7e324ef` + runde-luk-rettelsen `[luk3b:companion-13-401]`, `HFs-lenovo`)

Companionen henter tokenet via `/token/request` (Godkend på telefonen), gemmer det med DPAPI, kalder `/pair` og `/wd` med det, og genvejene genopretter via `/wd` efter en genstart.
Målt mod Note10: 401 uden og med forkert token, 200 med det rigtige; token-blokkens tre grene korrekte med `Request-Token` stubbet.
⚠️ **Ikke målt live:** selve Godkend-flowet, `/pair` og `/wd`-genopretningen. Første rigtige kørsel på en PC er beviset.
Eksisterende companion-installationer (også husets egne PC'er) skal hentes og køres igen; ellers fejler genvejene på en telefon med token.

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
| F-Droid | **Upstream på 1.4/55 via botten:** `!50097` »bot: Update Husk to 55« merget 2026-09-25 09:30 UTC af `linsui`, bygget og publiceret (`f-droid.org/api/v1/packages/co.xplat.husk` viser 55); vores `!50000` lukket uden merge af `linsui` 11 min. senere (målt 2026-09-27; begrundelsen er ikke læst). `AutoUpdateMode` virker altså, så en release behøver ikke egen MR |

⚠️ `check-api-parity.sh` så indtil 1.4 ikke ruter med to segmenter (tegnklassen manglede `/`); rettet.
⚠️ `git grep -c '"/token/'` fra Git Bash svarer 0, fordi MSYS sti-konverterer et argument med `/segment/` (måleregel 452-klassen), ikke fordi `"` tabes: `MSYS_NO_PATHCONV=1 git grep ...` svarer 3.
⚠️ **Kendt fejl til næste release (fundet af Trin 3, ikke rettet i 1.4):** `TokenRequests.decide` fornyer ikke `createdMs` ved Godkend, så en godkendelse tæt på 120 s kan nå at udløbe før klienten henter; på en tokenløs enhed er tokenet da SAT uden at nogen fik det (læs det i appens felt). Kur: sæt `createdMs = now` ved Godkend.

1.3 (2026-09-23, `4c42206`, MR `!49892` merget): kameraside huskes i prefs. Detaljer: `git show e650ff3`.

## Flåden 2026-09-27: hele flåden på 1.4 med token

**2026-09-27 (`HFs-lenovo`):** ejeren opdaterede begge spares til 1.4 og genererede et token i appen.
Hentet med `/token/request?client=claude-lenovo` + Approve på telefonen, lagt i vaulten som
`Husk token SM-A102U1` (opdateret) og `Husk token 702SO` (nyt). Målt med vault-værdien: `/info` 401 uden og
med forkert token, 200 med og viser 1.4/55 på begge. `/snapshot` 200 på begge (702SO svarede 503 første gang,
mens kameraet startede). `/screen.jpg`: 702SO 200; A10e svarede 503 »no screen frame« uden `wake`, og 200
efter `spare.sh a11 shot` (som vækker først). Årsagen kan være den sovende skærm ELLER den dovne skærm-producers
opstart (første kald vækker den selv); kontrollen, to kald i træk uden `wake`, er ikke taget.
Opdateringen til 1.4 lavede ejeren ved telefonerne; vejen (F-Droid-klient eller kabel) er ikke noteret.
Token-vejen i 1.4 bruges nu af alle tre enheder; `pc/spare.*` kræver derfor `HUSK_TOKEN` også mod spares.

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
- **SM-A102U1 (A10e)** `.101.102` og **Sony 702SO** `.101.101`: 1.4/55 med token siden 2026-09-27 (se ovenfor).
  A10e var nede fra `adb reboot` 2026-09-24 til ejeren låste den op; 702SO har ingen Wireless Debugging (Android 9).

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
2. **Token på spares – ✅ UDFØRT 2026-09-27:** begge på 1.4 med token i vaulten; målingen står i flåde-afsnittet.
3. **503-årsagen fra 2026-09-07 – AFSKREVET af ejeren 2026-09-23** (»Den er overflødig«). Genrejs den ikke.

## Adversarisk verifikation (`/luk-runde` Trin 3, 2026-09-27, `HFs-lenovo`)

Ad hoc-runde »token på spares«. Frisk `fable`-agent over `76a0439`, `362a1ca`, `1ed2db1` og `enheder.md`-diffen. Forrige rundes tabel (1.4-planen): `git show 1ed2db1:FORTSÆT-HER.md`.

| Linse | REFUTERET | Målt og gjort |
|---|---|---|
| Gate 1-3 | nej | kun docs; intet modul, ingen vendored kilde, intet template-bidrag |
| Gate 4 | delvist | F-Droid-bot-kapabiliteten stod kun i projektet; nu også i `infra/gitlab.md`. Memory `reference_husk_spare_wake_first` sagde »spares er tokenløse«; rettet kun i `~/.claude` (begge projektmapper); `.claude-k`, `.claude-dlm` og det kanoniske sæt får den ved næste memory-synk |
| Gate 7 | delvist | 14 forældede steder (fleet-doc, `BUILD.md`, `besluttede-opgaver.md` §2, `fdroid-fork-update.py`, denne fil); rettet |
| Gate 8 | nej | 0 tokens i diffen; `/info` 401 uden token på alle tre. Tokenet gik som `?token=` i lokal curl-argv under målingen, ikke via config-fil |
| Gate 9 | delvist | `pc/spare.sh shot` meldte en 401 som »skærmen sov«; rettet til `ERR HTTP <kode>` og målt (401, 200, 200). husk-viewer-docs sagde »INGEN token«; rettet |
| Gate 11 | delvist | `enheder.md` modsagde sig selv (»umålt«, »VALG«-afsnittet); rettet |
| Gate 9b (2 stemmer) | ja, begge | 503-årsagen og »lukket som overflødig/dublet« var stærkere end målingen; memory-påstanden for bred; `enheder.md`s gamle overskrift og A9-kabellinjen modsagde. Alt omformuleret. `spare.sh` grøn (401, curl-timeout, 200) |
| Egen måling | ja | min diagnose »skærmdeling slået fra« for A10e's `/screen.jpg` 503 holdt ikke: 200 efter `wake` (årsag: sovende skærm eller dovne producer, se flåde-afsnittet) |

**Trin 4 (`koer-tjek.sh --kun-nye`, baseline-filen, 81 kald: 47 målt, 34 sprunget):** NYE FUND 2, BAGGRUND 27, UKENDT 7.
NYE: `check-memory-spejl.ps1` var rundens (memory rettet i én home) og er grøn efter `sync-config-homes.ps1`; `check-relative-refs.sh` stod på synk-gaten (to deltag-repoer bagud) og er grøn efter scopet `sync-repos.sh`.
UKENDT: fire med `@tjek-korpus: ingen` og to uden feltet er baggrund pr. konstruktion; `check-trae-tilbagerulning.sh#1` målt særskilt: to filer i Kärnfull og dells paritetsmarkør, ingen fra runden. BAGGRUND: ti bash-tjek og tre PowerShell-tjek (de sidste scopet til `P_app_husk` og `-viewer`, rc=0) nævner ingen af rundens filer.

`P_kontor/docs/tailscale-migration.md:66` bytter `xperia-hfb` og `.102` i en tabel dateret 2026-05-24; lukket som historik, ikke rettet.

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
