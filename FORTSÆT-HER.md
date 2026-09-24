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

## Flåden 2026-09-24: begge spares på 1.3, ingen spare har token

Målt 2026-09-24 fra `HFs-lenovo` med `/info` på 8090 uden token. 2026-09-23-målingen fra `HFs_Dell` står i `docs/flaade-2026-09-23.tsv`.

- **Note10** `.103.102`: **1.2 / 53**, målt 2026-09-24 med `husk-rig-token` fra vaulten (uden token: `401`). Ejeren har opgraderet den; 2026-09-23 stod den på 1.0 / 51.
- **SM-A102U1** `.101.102` og **Sony 702SO** `.101.101`: online og **1.3 / 54** efter at ejeren har genforbundet
  og opdateret dem. Begge svarer `200` UDEN token, så ingen af dem har et endnu.

Tilbage:
1. **Token på BEGGE spares** (ejerens ordre 2026-09-24, udvider beslutningen fra 23-09).
   ⛔ **Headless adb findes ikke, målt 2026-09-24 fra `HFs-lenovo`:** `adb connect :15557` giver
   `offline` på begge, `:5555` afvises (10061), og `/wd` svarer `wd recovery failed` på begge, også
   efter `wake`. `/screen.jpg` svarer »No screen frame« trods `screen:true`, så UI'et kan ikke ses.
   702SO (Android 9) har intet Wireless Debugging og kræver USB én gang (`adb tcpip 5555`).
   Fremgangsmåde når adb er der:
   generér ≥24 tegn fra `/dev/urandom`, læg i vaulten FØR enheden (`put-login "husk token <enhed>"`),
   `adb shell settings put global husk_token`, installér 1.3. Negativ probe (uden token) skal give
   401/403, positiv 200 med 54. Mål samtidig `use_front` over en genstart.
   ⚠️ Tokenet lukker PC-webcam-klienten ude til den får det; skriv her hvilken enhed der fik et.
2. **`latest.json` i repoet har ingen læser længere:** `/info` viser ≥52 på alle tre (54, 54, 53,
   målt 2026-09-24), så betingelsen for at slette den er opfyldt. Ikke slettet endnu.

## BESLUTTEDE opgaver: status 2026-09-23

Opskrifterne står i `docs/besluttede-opgaver.md`.

1. **Afpublicér APK-assets til og med `v0.9.30` – ✅ UDFØRT 2026-09-23** efter ejerens ja.
   42 assets slettet (releases og tags står); listen og værnene: `docs/afpubliceret-2026-09-23.txt`.
   Eftermålt: tre stikprøver 404, `v0.9.31`/`v1.0`/`v1.1`/`v1.2`/`v1.3` alle 200.
2. **Token på spares – ÅBEN, blokeret af adb:** begge spares er online på 1.3/54 siden 2026-09-24, men ingen har token, og ingen af dem kan nås med adb headless (se flåde-afsnittet).
3. **503-årsagen fra 2026-09-07 – AFSKREVET af ejeren 2026-09-23** (»Den er overflødig«). Genrejs den ikke.

## Adversarisk verifikation (`/luk-runde` Trin 3, 2026-09-23, `HFs_Dell`)

Frisk `fable`-agent over rundens diff, planens lukke-betingelser og PLAN.md 4A.

| Linse | REFUTERET | Målt |
|---|---|---|
| S1, S2, S4-S7, S9 | nej | artefakter + live (begge `latest.json` 54, MR `!49892` mergeable, 42 releases uden assets, 31/31) |
| S3 | delvist | `grep -c Settings.Global` = 1 var sandt før rettelsen; ægte bevis: dex i 1.3 bærer `use_front` |
| S8 | delvist | registrets tekst sagde »ca. 5700 tilbage«; reelt 247. Rettet |
| Java (invariant C, tråde) | nej | `unavailableIds` kun på `camHandler`; ingen clobber via `loadMotionPrefs` |
| Gate 7, 9, 11 | delvist | handoff-overdrivelse, `P_xplat`-handoff på 1.2, Note10-token-vejen manglede i `infra/enheder.md`. Rettet / noteret ovenfor |
| Gate 1-4, 8 | nej | intet modul/template; læring skrevet; ingen ny PII eller flade |

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
