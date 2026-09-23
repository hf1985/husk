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
> afleveringen om `pc/udadvendt.py` er slettet her, fordi de er udført: `e5bacb9` og `S7`-commiten.

## ✅ 2026-09-23: 1.3 ER UDGIVET (`HFs_Dell`)

| Hvad | Tilstand |
|---|---|
| Version | **1.3 / versionCode 54**, tag `v1.3` på byggecommiten `4c42206`, bygget fra `git archive HEAD` |
| Hvad 1.3 retter | Kameraside huskes over en opdatering (`Rig.setUseFront` → prefs `husk`/`use_front`), og `requestFront` sætter `othersHaveCamera` ud fra det nye ids KENDTE tilstand frem for ubetinget `false`. Begge fund fra 2026-09-19; kode i `e5bacb9`. |
| Kanalen | ⚠️ Planen foreskrev `Settings.Global` som `husk_token`. Den kan appen kun LÆSE (skrivning kræver `WRITE_SECURE_SETTINGS`), så valget ligger i prefs: overlever en opdatering, ikke en afinstallation. |
| Signatur | `96195cfd…c17d`; asset hentet fra GitHub er sha256-identisk (`39c95f9f…1ef3`). 16 `uses-permission` som i 1.2. |
| xplat.co | deployet (`P_xplat` `e779e06`); begge `latest.json` viser 54; `check-api-parity.sh` grøn (43 endpoints). |
| F-Droid | **Ny MR `!49892`** fra en gren genskabt oven på upstream master (1 commit). Forkens pipeline `2876338895` grøn: F-Droid byggede 54 og verificerede mod vores binær. Venter på review. |
| Ikke målt | Opstarts-læsningen af `use_front` på en enhed. Mål: `/set?front=1`, genstart, `/flags.front`. |

⚠️ `pc/fdroid-fork-update.py`s dublet-tjek matchede `checkupdates-bot`s MR `!49420` (»Update Husk
to 52«, samme grennavn i botens eget projekt) og sprang vores MR over med exit 0. Rettet: kun MR'er
fra forken tæller. Bot-MR'en står åben med `conflict`; den er F-Droids, ikke vores.

## ⛔ Flåden 2026-09-23: intet er opgraderet, og ingen spare har token

Målt fra `HFs_Dell`, tabellen står i `docs/flaade-2026-09-23.tsv`:

- **Note10** `.103.102`: **1.0 / 51**, a11y og skærm oppe, token kræves. Bevidst ikke rørt (kontor-mødekameraet).
- **SM-A102U1** `.101.102` og **Sony 702SO** `.101.101`: **UNAAELIG** - intet svar på 8090,
  `tailscale ping` uden svar, tailnettet melder dem offline (sidst set 1 og 2 døgn før).

**Det kræver et menneske:** tænd eller genforbind de to spares. Derefter:
1. **Token på den spare der kan nås over Wireless Debugging** (ejerens beslutning 2026-09-23):
   generér ≥24 tegn fra `/dev/urandom`, læg i vaulten FØR enheden (`put-login "husk token <enhed>"`),
   `adb shell settings put global husk_token`, installér 1.3. Negativ probe (uden token) skal give
   401/403, positiv 200 med 54. Mål samtidig `use_front` over en genstart.
   ⚠️ Tokenet lukker PC-webcam-klienten ude til den får det; skriv her hvilken enhed der fik et.
2. **`latest.json` i repoet bliver stående:** ingen enhed viser 52 eller derover (Note10 51, to
   UNAAELIG). Slet den først når `/info` på alle tre viser ≥52.

## BESLUTTEDE opgaver: status 2026-09-23

Opskrifterne står i `docs/besluttede-opgaver.md`.

1. **Afpublicér APK-assets til og med `v0.9.30` – ✅ UDFØRT 2026-09-23** efter ejerens ja.
   42 assets slettet (releases og tags står); listen og værnene: `docs/afpubliceret-2026-09-23.txt`.
   Eftermålt: tre stikprøver 404, `v0.9.31`/`v1.0`/`v1.1`/`v1.2`/`v1.3` alle 200.
2. **Token på spares – ÅBEN**, blokeret af at ingen spare kan nås (se flåde-afsnittet).
3. **503-årsagen fra 2026-09-07 – AFSKREVET af ejeren 2026-09-23** (»Den er overflødig«). Genrejs den ikke.

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
