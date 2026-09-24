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
- **`P_xplat/FORTSÆT-HER.md` siger stadig »Husk-kataloget er på 1.2/53«**, mens koden er 1.3/54
  (`e779e06`). Ikke rettet, fordi filen bar en nabosessions uncommittede paysync-arbejde.
- `pc/udadvendt.py`: `laes_loft`s `ud`-parameter bruges ikke længere efter `757f5f7`. Kosmetisk.

## ✅ 2026-09-23: 1.3 ER UDGIVET (`HFs_Dell`)

| Hvad | Tilstand |
|---|---|
| Version | **1.3 / versionCode 54**, tag `v1.3` på byggecommiten `4c42206`, bygget fra `git archive HEAD` |
| Hvad 1.3 retter | Kameraside huskes over en opdatering (`Rig.setUseFront` → prefs `husk`/`use_front`), og `requestFront` sætter `othersHaveCamera` ud fra det nye ids KENDTE tilstand frem for ubetinget `false`. Begge fund fra 2026-09-19; kode i `e5bacb9`. |
| Kanalen | ⚠️ Planen foreskrev `Settings.Global` som `husk_token`. Den kan appen kun LÆSE (skrivning kræver `WRITE_SECURE_SETTINGS`), så valget ligger i prefs: overlever en opdatering, ikke en afinstallation. |
| Signatur | `96195cfd…c17d`; asset hentet fra GitHub er sha256-identisk (`39c95f9f…1ef3`). 16 `uses-permission` som i 1.2. |
| xplat.co | deployet (`P_xplat` `e779e06`); begge `latest.json` viser 54; `check-api-parity.sh` grøn (43 endpoints). |
| F-Droid | **Ny MR `!49892`** fra en gren genskabt oven på upstream master (1 commit). Forkens pipeline `2876338895` grøn: F-Droid byggede 54 og verificerede mod vores binær. **Merget 2026-09-24 07:34 UTC af `linsui`** (målt i GitLab-API'et fra `HFs-lenovo`). |
| Ikke målt | Opstarts-læsningen af `use_front` på en enhed. Mål: `/set?front=1`, genstart, `/flags.front`. |

⚠️ `pc/fdroid-fork-update.py`s dublet-tjek matchede `checkupdates-bot`s MR `!49420` (»Update Husk
to 52«, samme grennavn i botens eget projekt) og sprang vores MR over med exit 0. Rettet: kun MR'er
fra forken tæller. Bot-MR'en står åben med `conflict`; den er F-Droids, ikke vores.

## ⛔ Flåden 2026-09-24: alle tre på ≥52, A10e NEDE efter en genstart og kræver ejeren ved telefonen

Målt 2026-09-24 fra `HFs-lenovo`. 2026-09-23-målingen fra `HFs_Dell` står i `docs/flaade-2026-09-23.tsv`.

- **Note10** `.103.102`: **1.2 / 53**, målt med `husk-rig-token` fra vaulten (uden token: `401`).
- **Sony 702SO** `.101.101`: **1.3 / 54**, svarer `200` uden token. `/control` virker; adb-broen 15557 er `offline`
  fra både lenovo og dell (intet Wireless Debugging på Android 9).
- **SM-A102U1** `.101.102`: var **1.3 / 54**. ⛔ **NEDE siden `adb reboot` kl. 10:50** - hverken 8090 eller
  Tailscale kommer op (`rx 0` på tailnettet). Formodning, ikke målt: telefonen venter på første oplåsning
  efter boot, så hverken Husk eller Tailscale starter. Kræver ejeren fysisk ved telefonen.
  `husk_token` ER sat i `Settings.Global` (32 tegn, læst tilbage, identisk med vault-itemet
  `Husk token SM-A102U1`), men negativ/positiv probe er IKKE taget: før genstarten svarede `/info` 200
  uden, med og med forkert token, fordi appen kun læser feltet ved service-start.

⛔ **Rettelse af handoff-commit `1e7aa0e`:** »headless adb findes ikke« og »/screen.jpg
svarer No screen frame, så UI'et kan ikke ses« var FORKERT. `/control` (H.264) viser skærmen på begge
spares, og A10e er parret med `hf198@HFS_DELL`, så `adb connect 100.100.101.102:15557` lykkes fra dell
(ikke fra lenovo, hvis nøgle ikke er parret). `/screen.jpg` er MJPEG-vejen og var tom, mens H.264-vejen virkede.

⛔ **`adb reboot` på en spare kan tage den helt af nettet til nogen låser den op.** `CLAUDE.md`s
deploy-opskrift (»`adb install -r`, derefter `adb reboot`«) er skrevet til Note10-riggen; brug den ikke
på en spare uden at nogen er ved telefonen.

Tilbage:
1. **Husk 1.4 er PLANLAGT, ikke startet:** `_styresystem/planer/2026-09-24-husk-token-i-appen-plan.md`.
   Token-felt i appen, et API med godkendelse på telefonen, `Settings.Global`-vejen fjernet uden
   migrering (ejerens valg), »Hent fra telefonen« i husk-webcam, og Note10 opdateret headless.
   Ejerens svar står verbatim i planens Beslutninger; genforeslå dem ikke.
2. **A10e:** kræver ejeren ved telefonen. Når den er oppe, virker dens `Settings.Global`-token indtil
   1.4; efter 1.4 skal tokenet sættes i appen (værdien fra `Husk token SM-A102U1`).
   ⚠️ Tokenet lukker PC-webcam-klienten ude til den får det.
3. **`latest.json` i repoet:** betingelsen for sletning er opfyldt (53, 54, 54, målt 2026-09-24); planens
   `S6` sletter den.

## BESLUTTEDE opgaver: status 2026-09-24

Opskrifterne står i `docs/besluttede-opgaver.md`.

1. **Afpublicér APK-assets til og med `v0.9.30` – ✅ UDFØRT 2026-09-23** efter ejerens ja.
   42 assets slettet (releases og tags står); listen og værnene: `docs/afpubliceret-2026-09-23.txt`.
   Eftermålt: tre stikprøver 404, `v0.9.31`/`v1.0`/`v1.1`/`v1.2`/`v1.3` alle 200.
2. **Token på spares – ÅBEN:** A10e har feltet sat men er nede efter en genstart; 702SO har intet. Kuren er et token-felt i appen (1.4), se flåde-afsnittet.
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
