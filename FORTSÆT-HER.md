# P_app_husk – fortsæt her

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver xplat): den publicerede FOSS-app der gør en
gammel Android-telefon til fjernstyret kamera plus accessibility-automationsmotor plus
scrcpy/adb-bro over eget Tailscale-net, uden root. Overblik: `README.md`. Agent-kontekst,
invarianter og release-pligten: `CLAUDE.md` – **læs release-blokken øverst i den før du rører
app-koden**.

> **Denne fil er skåret ned 2026-09-20** fra 36.204 bytes efter ejerbeslutningen »Gate nyt, ryd
> kun det der læses udefra eller ved hver sessionsstart«. Historikken er flyttet ORDRET til
> `docs/versionshistorik.md` under overskriften »Arkiveret fra FORTSÆT-HER.md 2026-09-20«, og
> intet er slettet. Her står kun det der stadig er sandt eller stadig er udestående.

## ✅ 2026-09-20: 1.2 ER UDGIVET

Kørt på `hfs-dell` efter `_styresystem/planer/2026-09-19-husk-udgiv-loekkefix-plan.md`.

| Hvad | Tilstand |
|---|---|
| Version | **1.2 / versionCode 53**, tagget på byggecommiten `390d9c5e` |
| Hvad 1.2 retter | `CameraService.requestFront` kaldte `h.post(demandCheck)`, og `demandCheck` genplanlægger sig selv med `postDelayed(this, 1000)`. N sideskift gav N+1 samtidige 1-sekunds-løkker, i strid med invariant C. Kuren er ét kodested, `planlaegDemandCheck()`, som fjerner ventende kald før den poster. Fejlen sad i den UDGIVNE 1.1-APK. |
| Hvad 1.2 IKKE ændrer | `ControlServer.java` er byte-uændret fra `v1.1`, så ingen nye endpoints, params, respons eller adgangsmodel. `AndroidManifest.xml` og `MainActivity.java` ændrer kun kommentarer. Tilladelses-listen er identisk (17 mod 17). |
| Signatur | `96195cfd…c17d`. Den HENTEDE fil fra GitHub er sha256-identisk med den signerede. |
| xplat.co | deployet; `https://xplat.co/husk/latest.json` viser 53, `check-api-parity.sh` grøn (43 endpoints). |
| F-Droid | **MR !49350** er åben, `mergeable`, 2 commits, og bærer entries for 52 OG 53. Fork-pipelinen reproducerede begge mod referencebinæren med den tilladte signer (job `16607081166`). |
| Flåden | ⚠️ **ingen enhed er på 1.2.** Kun spare SM-A102U1 (.101.102) er på 1.1; Note10 og Sony 702SO er på 1.0. |

### ⛔ To ting der kræver et menneske, og som IKKE er gjort

1. **Note10 og A9 er ikke opgraderet, og det var et VALG.** Note10 er kontor-mødekameraet: en
   opdatering dræber app-processen, og DeX-rig'en har historik for at tabe a11y, scrcpy og
   Discord. A9 (Sony 702SO) kan afbinde a11y ved en opdatering og har **ingen Wireless
   Debugging**, så en fejl kræver et USB-kabel på stedet. Planlæg dem fysisk, ikke remote.
2. **`latest.json` kan først slettes når HELE flåden er på 1.1 eller derover.** Målingen der
   frigiver den: `/info` viser 52 eller derover på hver enhed. Se tabellen i `CLAUDE.md`.

## ⛔ Tre BESLUTTEDE opgaver der mistede deres eneste levende peger 2026-09-19

Disse tre stod som `SPOR:`-punkter i en genereret blok øverst i denne fil. Blokken blev fjernet af
`check-plan-pointers.sh` i commit `4cb5b24`, fordi generatoren ikke længere kunne se deres plan:
`2026-09-07-husk-fdroid-restfund-plan.md` blev arkiveret med hele plankøringssystemet til
`_arkiv/2026-09-13-plankoeringssystemet/planer/lukkeplaner/`. **Generatoren regenererer dem derfor
aldrig.** De er skrevet ud i fuld form her, fordi en peger til en arkiveret plan ikke er en udgang.

**Alle tre er BESLUTTET** (B1 og B3 i den arkiverede plan); det der mangler, er udførelsen.

### 1. Afpublicér APK-assets til og med `v0.9.30` (var `S1`) – SIKKERHED

Kodeordet til den pensionerede signeringsnøgle (`CN=Debug, O=KHFRB`, alias `ad`, SHA-256
`1b89a920…62af59`) kan hentes fra det OFFENTLIGE repos git-historik. Keystore-filen selv har aldrig
været committet, men **alle releases til og med `v0.9.30` er signeret med den nøgle og er stadig
downloadbare**, så enhver kan signere en APK der ser gyldig ud for en gammel installation.

⚠️ **Ikke udført, og bevidst ikke udført af en uovervåget session:** det er en irreversibel
bulk-sletning af offentlige artefakter. Beslutningen er truffet; udførelsen kræver et menneske der
siger ja, og en session der måler FØRST.

1. Opregn før du sletter: `gh release list --repo hf1985/husk --limit 60`, og pr. release
   `gh release view <tag> --repo hf1985/husk --json assets -q '.assets[].name'`. Skriv listen til
   en fil og TÆL den. Forventet: 28 releases `v0.9.1`-`v0.9.30`.
2. **Fjern APK-ASSETTET, ikke release-noten** – assettet bærer angrebsfladen, noten er historik:
   `gh release delete-asset <tag> <asset-navn> --repo hf1985/husk --yes`.
3. Skriv én linje i hver berørt note om hvorfor assettet er væk
   (`gh release edit <tag> --notes-file <fil>`).
4. ⛔ **Rør ALDRIG `v0.9.31` eller nyere** – de er signeret med den nye nøgle, og F-Droids
   `Binaries:` henter deres assets. Sletter du et af dem, brækker du indsendelsen.
5. Ryd modsigelsen i `docs/BUILD.md` om hvor længe debug-keystoren skal blive. Flåden ER
   geninstalleret. Slet restkopier med `bash _styresystem/scripts/safe-delete.sh --shred <sti>`.

**Verifikation:** `v0.9.30` viser ingen APK-assets, `v0.9.31` viser stadig sin, og
`curl -sI .../v0.9.31/husk-v0.9.31.apk` svarer 302.

⚠️ **`gh`s gemte token var UGYLDIGT 2026-09-19** (`gh auth status`: »The token in default is
invalid«). Git kan stadig pushe, fordi Windows Credential Manager bærer en brugbar `ghp_`-PAT, som
kan hentes med `printf 'protocol=https\nhost=github.com\n\n' | git credential fill`. Den virkede
til at oprette en release og lægge et asset op. Mål det FØR du planlægger en bulk-sletning.

### 2. Sæt et token på de to spares (var `S2`) – SIKKERHED

Tokenet er tomt som standard, så kilde-IP-ACL'en er eneste spærre – og den lukker hele
`100.64.0.0/10` ind, ikke kun vores eget tailnet. **Det står nu offentligt i MR-tråden.** Genmålt
2026-09-19: `/info` og `/flags` svarer stadig 200 uden token på begge spares.

1. Generér ét token pr. enhed (mindst 24 tegn, alfanumerisk – `ControlServer.sanitizeToken`
   stripper alt andet).
2. Gem dem i vaulten som login-items **FØR** du sætter dem:
   `bash Tools/vault2/vault2.sh put-login "Husk token 702SO" husk <fil>` (og for `SM-A102U1`).
   Aldrig i en fil i repoet.
3. `adb -s <serial> shell settings put global husk_token '<token>'` – værdien bor i
   `Settings.Global` og **overlever en afinstallation**.
4. Læs tilbage, og efterprøv **begge retninger**: `/info` skal svare **401 uden** token og 200 med.
   Den negative probe er hele pointen (måleregel 1).
5. Ret `pc/spare.sh` og `pc/spare.ps1` så de sender tokenet. Søg efter flere forbrugere med
   `grep -rn "8090" P_app_husk/pc P_kontor` før du erklærer dig færdig.

### 3. Hvorfor kom kamera- og skærmtjenesten ikke op efter opdateringen? (var `S9`)

MÅLT 2026-09-07: efter in-app-opdateringen til 1.0 svarede `/snapshot` 503 på BEGGE spares (også på
andet kald), og `/screen.jpg` var uden frame på SM-A102U1. Porten var oppe hele tiden, så flåden så
sund ud udefra. Et tap på »Camera streaming« kurerede kameraet.

⛔ **Årsagen er IKKE målt.** Den første forklaring (»ScreenService hostede 8090 alene«) er en
hypotese der er trukket tilbage: `BootReceiver` starter `CameraService` ubetinget ved
`MY_PACKAGE_REPLACED`. Og fordelingen taler imod den: det ramte A9 og A11, ikke A12.

⚠️ **Reproduktionen fra den arkiverede plan kan ikke længere køres som skrevet.** Trin 1 var
»opdatér en spare in-app«, og `/update` er FJERNET i Husk 1.1. Tilstanden må nu fremprovokeres med
en almindelig `adb install -r` eller en F-Droid-opdatering. Mål derefter med
`adb shell dumpsys activity services co.xplat.husk` og `adb logcat -d` umiddelbart efter, og afgør
om det er Android 12+'s baggrunds-FGS-restriktion eller noget andet. Resultatet hører i
`docs/fleet-tailnet-transport.md` §5, som i dag påstår at J4-self-healen er komplet.

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

## Hvor resten står

- **Release-proceduren, invarianterne og flåde-tabellen:** `CLAUDE.md`. Gentag dem ikke her.
- **Build, signering, F-Droid-CI:** `docs/BUILD.md`.
- **Ydelses-invarianter og sikker rig-deploy:** `docs/YDELSE-OG-DRIFT.md`.
- **Flåde og tailnet-transport:** `docs/fleet-tailnet-transport.md`.
- **Al historik** – status frem til 1.0, MR !40810, review-kittets ni fund, nøgleskiftet 2026-09-04
  og den adversariske verifikation af 2026-09-19: `docs/versionshistorik.md`.
- **GitLab-fælder og MR-arbejdsgangen:** `_styresystem/infra/gitlab.md`.
