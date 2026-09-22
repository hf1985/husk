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


## Aflevering 2026-09-20 fra governance-runden `korthed-med-et-maalt-loft`: død peger i `pc/udadvendt.py`

`laes_loft`s `MIDLERTIDIGE`-blok (l. 53) og dens advarselstekst navngiver »spor `K1` i
`_styresystem/planer/2026-09-19-korthed-med-et-maalt-loft-plan.md`« som sin kur.
**Sporet er lukket, og planen er retireret** (slettet ved runde-lukket samme dag), så pegeren er død.

**Målt 2026-09-20 på `HFs_Dell` efter at `K1` lagde rækkerne ind:** alle tre defaults resolver nu
fra registret uden en advarsel – `mr-beskrivelse-loft` 800, `mr-kommentar-loft` 400,
`mr-titel-loft` 200 (den sidste blev lagt ind netop fordi denne kode navngav sporet som kur for
alle tre). Blokken er altså død kode med en død peger.

**Kuren, når nogen alligevel rører filen:** fjern `MIDLERTIDIGE` og den gren der læser den, så et
manglende loft bliver en ren `Afvist` frem for en tavs default. Behold `laes_loft`s øvrige tre
udfald. Er blokken i stedet ment som et værn mod at registret mangler en ny klasse, så skriv det
som DET frem for som en henvisning til et lukket spor.

**Og et beslægtet fund til samme fil:** `laes_loft` (python) og
`_styresystem/scripts/check-claudemd-size.sh --loft` (bash) er nu TO læsere af samme kolonne i
`konstanter.tsv`, med hver sin parser og hver sin fejlbesked. Ingen vagt måler at de bliver enige
(måleregel 15). Kuren kræver en runde der ejer begge flader; den er registreret i
`_styresystem/FORTSÆT-HER.md` 2026-09-20 som fund 2.

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

## ⛔ Tre BESLUTTEDE opgaver der endnu ikke er udført

Alle tre er **BESLUTTET** (`B1`/`B3` i en plan der siden er arkiveret); det der mangler, er
udførelsen. De mistede deres eneste levende peger 2026-09-19, da den genererede `SPOR:`-blok blev
fjernet og generatoren ikke længere kunne se deres arkiverede plan. **Derfor står de her.**

⛔ **Fremgangsmåde, verifikation og faldgruber for hver af dem: `docs/besluttede-opgaver.md`.**
Opskrifterne fylder 5 KB og flyttede dertil 2026-09-19, fordi denne fil auto-loades i hver agents
kontekst og har et loft på 12.000 bytes. Påstanden om at opgaven findes, hører hvor den læses.

1. **Afpublicér APK-assets til og med `v0.9.30` – SIKKERHED.** Kodeordet til den pensionerede
   signeringsnøgle kan hentes fra det OFFENTLIGE repos git-historik, og alle releases til og med
   `v0.9.30` er signeret med den nøgle og stadig downloadbare. ⚠️ **Ikke udført, og bevidst ikke
   af en uovervåget session:** det er en irreversibel bulk-sletning af offentlige artefakter.
   ⛔ Rør ALDRIG `v0.9.31` eller nyere - F-Droids `Binaries:` henter deres assets.
2. **Sæt et token på de to spares – SIKKERHED.** Tokenet er tomt som standard, så kilde-IP-ACL'en
   er eneste spærre, og den lukker hele `100.64.0.0/10` ind. **Det står nu offentligt i
   MR-tråden.** Genmålt 2026-09-19: `/info` og `/flags` svarer stadig 200 uden token på begge.
3. **Hvorfor kom kamera- og skærmtjenesten ikke op efter opdateringen?** Målt 2026-09-07:
   `/snapshot` svarede 503 på begge spares, mens porten var oppe hele tiden, så flåden så sund ud
   udefra. ⛔ **Årsagen er IKKE målt**, og den første forklaring er trukket tilbage.

## To åbne fund i koden (2026-09-19, ikke udført)

Begge er ægte, udførbart arbejde på denne flade som **ingen har besluttet skal gøres**. De bestod
ikke nødvendigheds-prøven i `2026-09-19-husk-udgiv-loekkefix-plan.md` og står derfor her frem for i
en plan.

### 1. `requestFront` sætter `othersHaveCamera = false` ubetinget

⛔ **En KODELÆSNING, ikke en måling.** Fundet af en adversarisk verifikator og efterprøvet på disk
2026-09-19: `requestFront` sætter `othersHaveCamera = false` ubetinget (`CameraService.java` l. 268)
og vælger derefter et nyt `targetCamId`, mens availability-callbacken (l. 283-290) kun latcher for
det id der ER `targetCamId` når hændelsen kommer. Holder en anden app allerede den NYE sides
kamera, siger flaget »ledig«, og `demandCheck` kalder `openCamera` på et optaget kamera – hvilket
er invariant C's egen grænse. **Mål før du retter**, og husk at linjenumrene er fra `v1.2`.

### 2. `Rig.useFront` persisteres ikke – et sideskift tabes ved procesgenstart

`Rig.java` l. 39 er en bar `static volatile boolean`, og den sættes kun tre steder: intent-extraet i
`CameraService`, `/set` i `ControlServer`, og `requestFront`. Ingen af dem skriver til
`Settings.Global` eller til en preference. **Installationen af en ny version nulstiller derfor selv
valget:** `MY_PACKAGE_REPLACED` genstarter processen, og kameraet er tilbage på bagsiden uden at
nogen rørte `/set`. Kuren ville være den samme kanal som `husk_token` bruger: `Settings.Global`, som
overlever en afinstallation.

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

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
