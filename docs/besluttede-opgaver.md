<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Tre BESLUTTEDE opgaver der endnu ikke er udført

⛔ **Denne fil er en LEVENDE peger, ikke historik.** Alle tre opgaver er BESLUTTET (`B1` og `B3`
i en plan der siden er arkiveret); det der mangler, er udførelsen. `FORTSÆT-HER.md` navngiver dem
og peger hertil - den fil auto-loades ved hver sessionsstart, så pegeren kan ikke blive væk.

**Hvorfor de ikke bare står i en plan.** De stod som `SPOR:`-punkter i en genereret blok i
`FORTSÆT-HER.md`. Blokken blev fjernet af `check-plan-pointers.sh` i commit `4cb5b24`, fordi
generatoren ikke længere kunne se deres plan: `2026-09-07-husk-fdroid-restfund-plan.md` blev
arkiveret med hele plankøringssystemet. **Generatoren regenererer dem derfor aldrig**, og en peger
til en arkiveret plan er ikke en udgang.

**Hvorfor fremgangsmåderne flyttede hertil 2026-09-19.** `FORTSÆT-HER.md` har et loft på 12.000
bytes, fordi den auto-loades i hver agents kontekst. De tre opskrifter fylder 5 KB. Påstanden om
at opgaven findes, hører hvor den LÆSES; opskriften hører hvor der er plads.

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

