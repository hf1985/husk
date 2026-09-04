<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Sådan bygges Husk (build-procedure)

Denne fil dokumenterer hvordan Husk-APK'en bygges, så en ny session kan tage over uden
at gætte. Den **kanoniske** build er Gradle `assembleRelease` kørt i WSL – nøjagtig samme
vej som F-Droids byggeserver. Der findes også en hurtig on-phone dev-build, men den er
**ikke** den officielle vej.

> TL;DR – byg den aktuelle kilde til en unsigned release-APK (validerede kommandoer,
> kørt fra Bash-toolet / git-bash, jf. memory [[ps-wsl-quoting]]):
> ```bash
> # A) kopiér kilde G: -> WSL (git-bash læser G: stabilt; /mnt/g i WSL er upålideligt):
> wsl.exe --cd '~' -- bash -lc 'rm -rf ~/android-build/husk-build && mkdir -p ~/android-build/husk-build'
> cp -r "/g/My Drive/10_PROJEKTER/P_app_husk/"{app,gradle,*.gradle,gradle.properties,gradlew} \
>       "//wsl.localhost/Ubuntu/home/hf198/android-build/husk-build/"
> # B) byg i WSL:
> wsl.exe --cd '~' -- bash -lc 'source ~/android-build/env21.sh
>   echo "sdk.dir=$ANDROID_HOME" > ~/android-build/husk-build/local.properties
>   cd ~/android-build/husk-build && chmod +x gradlew && ./gradlew --no-daemon assembleRelease'
> ```
> Output: `~/android-build/husk-build/app/build/outputs/apk/release/app-release-unsigned.apk`
> (= præcis det F-Droid producerer). Med varm cache ~40 s. Signering: se afsnit 5.
>
> Når `/mnt/g` tilfældigvis er læsbar fra WSL kan helper-scriptet gøre A+B i ét:
> `wsl.exe --cd '~' -- bash -lc 'bash "/mnt/g/My Drive/10_PROJEKTER/P_app_husk/gradle-build.sh"'`
> (scriptet fejler pænt med kopi-instruktionen hvis /mnt/g er nede).

---

## 0b. Fuld release-recept + Windows/WSL-gotchas (verificeret 2026-07-11, v0.9.26)

Denne recept tog v0.9.26 hele vejen (byg -> signér -> begge `latest.json`-endpoints -> F-Droid-CI
grøn). Følg den, så rammer du ikke de samme faldgruber igen. **Kør ALT fra Bash-værktøjet
(git-bash), ikke PowerShell.**

**Windows/WSL-gotchas der bider (med fix):**
1. **`wsl.exe` inline med metakarakterer mangler.** En kommando med `|`, `(`, `)`, `;`, `&&` eller `"`
   sendt som `wsl.exe -- bash -lc '... | grep ...'` brækkes på vej over git-bash -> wsl.exe ->
   WSL-bash. Symptom: `bash: line N: : command not found`. **Fix:** skriv logikken til en fil og kald
   den metakarakter-frit. Robust mønster (LF-heredoc direkte til WSL-fs, kør via `~`):
   ```bash
   cat > "//wsl.localhost/Ubuntu/home/hf198/x.sh" <<'EOF'
   #!/usr/bin/env bash
   ... din logik med pipes/parens frit ...
   EOF
   wsl.exe --cd '~' -- bash -lc 'bash ~/x.sh'
   ```
2. **MSYS path-conversion.** `wsl.exe -- bash /home/hf198/x.sh` -> git-bash omskriver `/home/...` til
   `C:/Program Files/Git/home/...` -> "No such file or directory". **Fix:** brug `~/x.sh` INDE i den
   single-quotede `bash -lc '...'` (tilde ekspanderer på WSL-siden), eller sæt `MSYS_NO_PATHCONV=1`.
3. **`python3` er MS Store-stubben** i git-bash ("Python was not found..."). Brug **`py -3.11`**
   (Windows-launcher) til alt Python på PC-siden (fx F-Droid-API-scriptet).

**Adgange (flyttede efter secrets-off-computer 2026-07-10):**
- **Signeringsnøgle:** release-keystoren `~/android-build/husk-signing/husk-release.jks`
  (alias `husk`). **Kodeordet står ikke i denne fil** - hverken det eller keystoren; begge hentes
  fra vaulten. Se afsnit 5. Den pensionerede debug-keystore er beskrevet samme sted.
- **Asura-SSH-nøglen findes TRE steder** (rettet 2026-09-04; her stod »ligger nu KUN i
  ssh-agenten«, hvilket er falsk og kostede en fejlmelding): i WSL
  `~/.ssh/khfrb_asura_openssh`, i vault-item `ssh: khfrb_asura_openssh`, og som DPAPI-blob
  `$HOME/Tools/vault2/asura-ssh.dpapi`. Kanonisk: `_styresystem/infra/asura.md`.
- **GitLab-PAT ligger nu i vaulten**, ikke `Tools/gitlab/token.txt` (tom). Hent:
  `bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt'` (grep `glpat-...` ud; hardcode aldrig).

**Recepten (hver linje verificeret 2026-07-11):**
1. Bump `versionCode` + `versionName` i `app/build.gradle` (ÉT sted).
2. Kopiér kilde G:->WSL + byg (afsnit 3-4): `assembleRelease` -> unsigned APK.
3. **Signér via et WSL-script** (IKKE inline, jf. gotcha 1): skriv `sign-verify.sh` til `~` i WSL og
   kør `wsl.exe --cd '~' -- bash -lc 'bash ~/sign-verify.sh'`. Verificér cert-digest =
   `96195cfd...c17d` (release-noeglen; ellers afviser Android opdateringen). Det gamle
   `1b89a920...62af59` er den PENSIONEREDE debug-noegle - se afsnit 5.
4. Kopiér den signerede APK til repoets `husk-latest.apk`:
   `cp "//wsl.localhost/Ubuntu/home/hf198/android-build/husk-build/husk-vX.apk" "/g/My Drive/10_PROJEKTER/P_app_husk/husk-latest.apk"`.
5. Opdatér `latest.json` (ny versionCode) + `fdroid/co.xplat.husk.yml` (ny Builds-entry
   `commit: vX.Y.Z` + `CurrentVersion`/`CurrentVersionCode`; **quoting:** to-punktums-version som
   `0.9.26` er UNQUOTED).
6. Opdatér `HUSK_VERSION_NAME`/`HUSK_VERSION_CODE` i `P_xplat/hosting/app.py`, kør
   `bash scripts/check-local.sh` (grøn), og **deploy xplat.co** med agenten loaded:
   `eval "$(cat ~/.ssh/agent.env)"; bash scripts/hosting-deploy.sh --apply` (fra `P_xplat`).
6b. **API-DOK-GATE (obligatorisk, ellers driver `/husk/api` fra virkeligheden):** ændrer releasen
   endpoints/params/respons/adgangsmodel? Ajourfør `HUSK_API`-kataloget + OpenAPI-beskrivelsen i samme
   `P_xplat/hosting/app.py` (driver BÅDE `/husk/api` OG `/husk/openapi.json`) og re-deploy. Kør
   **`bash pc/check-api-parity.sh`** (fra `P_app_husk`) → **skal være GRØN** (fejler ved
   udokumenterede/stale endpoints + versionCode-drift). Verificér `/husk/api` live.
7. Commit (brug `git commit -F <fil>` med dansk besked, så æøå ikke mangler) + `git tag vX.Y.Z` +
   `git push origin main` + `git push origin vX.Y.Z`.
8. **F-Droid-fork uden clone** (fdroiddata er for stor at klone): opdatér
   `hf16/f-droid:metadata/co.xplat.husk.yml` (notationen er `repo:sti-i-det-repo` – filen ligger
   IKKE i dette repo, kun i fdroiddata-forken) på branch `co.xplat.husk` via GitLab **commits-API**
   (`POST /projects/hf16%2Ff-droid/repository/commits`, `action: update`), og poll pipelinen til
   `success`. Kør med `py -3.11` + `urllib` (mønster: denne release-sessions `fdroid-update.py`).
9. **Definition af færdig:** `curl https://xplat.co/husk/latest.json` OG
   `curl https://raw.githubusercontent.com/hf1985/husk/main/latest.json` viser BEGGE den nye
   `versionCode`, F-Droid/GitLab-pipelinen er grøn, **OG API-dok-gaten (trin 6b) er grøn +
   `/husk/api` er ajour**. (Erfaring 2026-07-12: en release bumpede versionen men glemte 5 nye
   endpoints + CSRF-adgangsmodellen i `/husk/api`; `pc/check-api-parity.sh` fanger netop dét.)

---

## 1. De to build-veje

| Vej | Script | Bruges til | Værktøjer |
|-----|--------|-----------|-----------|
| **Gradle (kanonisk)** | `gradle-build.sh` (WSL) | Release, F-Droid, GitHub, in-app-update | AGP 8.5.2 + Gradle 8.7 + JDK 21 + Android SDK |
| On-phone dev | `build.sh` (Termux) | Hurtig test direkte på en telefon | aapt2 → ecj → dx → apksigner |
| On-phone deploy | `setup.sh` (Termux) | build + install + permissions + start på en rig | kalder `build.sh` |

Resten af dokumentet handler om **Gradle-vejen**. `build.sh`/`setup.sh` er selvforklarende i
deres egne header-kommentarer; bemærk dog advarslen i `setup.sh` om aldrig at starte
MainActivity i forgrunden på en kørende DeX-rig.

---

## 2. Projektets build-konfiguration (allerede i repo'et)

- `build.gradle` (rod): `com.android.tools.build:gradle:8.5.2` (AGP).
- `app/build.gradle`: `namespace co.xplat.husk`, `compileSdk 34`, `minSdk 26`, `targetSdk 34`,
  `sourceCompatibility/targetCompatibility = JDK 17`, `minifyEnabled false`,
  `dependenciesInfo { includeInApk false }` (reproducerbar F-Droid-build).
- **Ingen `dependencies`-blok** – Husk er en ren framework-app uden AndroidX. Det er hele
  grunden til at F-Droid-buildet er nemt: ingen eksterne artefakter at hente eller verificere.
- `gradle.properties`: `android.useAndroidX=false`, `org.gradle.caching=true`, `Xmx2048m`.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle **8.7** (wrapper-jar er committet, så
  `./gradlew` virker offline efter første hentning).
- Versionen bumpes ÉT sted: `versionCode` + `versionName` i `app/build.gradle`.

---

## 3. Byggemiljø (WSL, sudo-frit, engangs-opsætning)

Alt ligger under `~/android-build/` i WSL (Ubuntu). Det er sat op uden root via nedladede
tarballs. Mappen indeholder:

```
~/android-build/
  jdk-17.0.19+10/         # Adoptium JDK 17 (env.sh)
  jdk-21.0.11+10/         # Adoptium JDK 21 (env21.sh) – brug denne til AGP 8.5
  sdk/                    # Android SDK (cmdline-tools, platform-tools, emulator)
    platforms/            # android-33, android-34
    build-tools/          # 30.0.3, 33.0.2, 34.0.0  (apksigner ligger i 34.0.0)
  gradle-7.6.4/           # standalone Gradle (legacy; wrapperen bruger 8.7)
  dl/                     # cache: jdk17/21.tar.gz, cmdtools.zip, gradle.zip
  env.sh   env21.sh       # exporterer JAVA_HOME / ANDROID_HOME / PATH
```

`env21.sh` (den vi bruger):
```sh
export JAVA_HOME="/home/hf198/android-build/jdk-21.0.11+10"
export ANDROID_HOME="/home/hf198/android-build/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

**Hvis miljøet skal genskabes fra bunden** (ny maskine / wiped WSL):
1. Hent Adoptium JDK 21 (tar.gz), pak ud i `~/android-build/`.
2. Hent Android `commandline-tools` (zip), læg i `sdk/cmdline-tools/latest/`.
3. `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`
   (acceptér licenser; `platforms;android-33` + `build-tools;33.0.2` er også installeret).
4. Skriv `env21.sh` som ovenfor.
5. **Gotcha:** python-zip-udpakning strippede exec-bit på `gradlew`/`gradle` → `chmod +x` dem.

---

## 4. Sådan kørte/kører selve Gradle-buildet

Historisk blev hver version bygget i sin egen mappe (`~/android-build/husk-vNN/`): en frisk
kopi af projektet, `local.properties` med `sdk.dir`, derefter `./gradlew assembleRelease`
logget til `~/android-build/hbNN.log`. Et typisk vellykket log slutter med:

```
> Task :app:assembleRelease
BUILD SUCCESSFUL in 5m 23s
40 actionable tasks: 29 executed, 11 from cache
```

`gradle-build.sh` automatiserer dette. Scriptet:
1. `source ~/android-build/env21.sh` (JDK 21 + SDK på PATH).
2. Synker projektet (rsync/cp, ekskl. `.git/ build/ .gradle/ obj/ bin/ *.apk`) ind i
   `~/android-build/husk-build/` – **men kun hvis `/mnt/g` kan læses**. Kan den ikke (det er
   den normale tilstand), fejler scriptet pænt og udskriver git-bash-kopikommandoen; kopiér
   så manuelt (afsnit 3 nedenfor) og kør igen med `HUSK_NOSYNC=1`.
3. Skriver `local.properties` med `sdk.dir=$ANDROID_HOME`.
4. `./gradlew --no-daemon assembleRelease`.
5. Verificerer at `app/build/outputs/apk/release/app-release-unsigned.apk` blev produceret.
6. Signerer hvis `HUSK_KEYSTORE` er sat (ellers stopper det ved den unsigned APK).

**Den robuste manuelle vej (verificeret 2026-06-23 – byggede v0.9.17 på ~40 s):**
```bash
# 1) synk G: -> WSL fra git-bash (UNDGÅR /mnt/g):
wsl.exe --cd '~' -- bash -lc 'rm -rf ~/android-build/husk-build && mkdir -p ~/android-build/husk-build'
cp -r "/g/My Drive/10_PROJEKTER/P_app_husk/"{app,gradle,*.gradle,gradle.properties,gradlew} \
      "//wsl.localhost/Ubuntu/home/hf198/android-build/husk-build/"
# 2) byg i WSL:
wsl.exe --cd '~' -- bash -lc 'source ~/android-build/env21.sh
  echo "sdk.dir=$ANDROID_HOME" > ~/android-build/husk-build/local.properties
  cd ~/android-build/husk-build && chmod +x gradlew && ./gradlew --no-daemon assembleRelease'
```
Bemærk: `~/.gradle/wrapper/dists/gradle-8.7-bin` er allerede cache'et, så Gradle hentes ikke
igen. `husk-build/.gradle` (projekt-cache) gør gentagne builds hurtige; ryd den ikke unødigt.

---

## 5. Signering (vigtigt for in-app-opdatering)

F-Droid signerer selv med sin egen nøgle – til F-Droid behøves **ingen** signering fra os;
den unsigned APK er nok.

Til **GitHub-releases** og repo'ets `husk-latest.apk` (det in-app-updateren henter) signerer
vi med `apksigner`. **Alle versioner SKAL signeres med samme nøgle**, ellers afviser Android
opdateringen (signaturskift = "app not installed").

### Den kanoniske signeringsnøgle (UDSKIFTET 2026-09-03)

```
Alias:    husk
Owner:    CN=xplat, O=xplat, C=DK
Type:     PKCS12, RSA 4096, gyldig til 2056-08-26
SHA-256:  96:19:5C:FD:54:0E:75:F8:A3:4D:FC:08:76:44:38:76:9D:4B:C3:E5:D7:97:0A:95:27:D9:70:04:D4:F2:C1:7D
```

**Adgangskoden står IKKE her.** Den ligger i vaulten sammen med selve keystoren:

```
bash ~/Tools/vault2/vault2.sh get-password "Husk release-signeringsnoegle (keystore husk-release.jks, base64)"
bash ~/Tools/vault2/vault2.sh get          "Husk release-signeringsnoegle (keystore husk-release.jks, base64)"   # base64 af .jks
```

Noten er base64 af `husk-release.jks`; dekod med `base64 -d` for at genskabe filen på en frisk
maskine. Round-trip er verificeret 2026-09-03: `sha256` af vault-kopien er identisk med den
lokale keystore (`11ff89eb…c554`, 4276 bytes). Nøglen opfylder dermed
maskin-uafhængigheds-invarianten: den kan genskabes fra vaulten alene.

Lokal arbejdskopi: `~/android-build/husk-signing/husk-release.jks` (mode 600, mappe 700).

> **Hvorfor den blev skiftet.** Den gamle nøgle var en genbrugt **debug**-keystore
> (`CN=Debug, O=KHFRB, C=DK`, alias `ad`) hvis kodeord stod i klartekst netop i denne fil, i et
> OFFENTLIGT repo. F-Droid-indsendelsen (MR !40810) pinner certifikatet permanent via
> `AllowedAPKSigningKeys`, og at skifte nøgle EFTER publicering brækker opdateringer for alle
> installationer. Vinduet var derfor før første publicering, ikke efter.
>
> **Betingelsen er UDLØBET 2026-09-04: alle tre enheder er geninstalleret på den nye nøgle**, så
> ingen installation bærer længere den gamle. Her stod at kopierne »skal blive liggende, indtil
> hver enhed er geninstalleret«. Kopiernes placering er IKKE målt herfra: på `HFs-lenovo` findes
> hverken `~/android-build/husk-signing/debug.keystore` eller `~/husk/debug.keystore` på Note10,
> så de ligger enten kun på `hfs-dell` eller er allerede væk. **Mål det dér før du sletter noget**
> (måleregel 65: skriv maskinen med). Den eneste tilbageværende grund til at beholde nøglen er en
> rollback til 0.9.30 eller derunder.

> ⚠️ **MÅ ALDRIG committes** (`.gitignore` ekskluderer `*.keystore` og `*.jks`). Mistes nøglen,
> kan ingen eksisterende installation opdateres in-app igen (signaturskift →
> »app not installed«), og F-Droid-builden fejler, fordi den udgivne APK så ikke længere bærer
> den pinnede signatur.

Signér en build (adgangskoden hentes fra vaulten og makuleres bagefter; `--key-pass` udelades,
fordi PKCS12 bruger samme kodeord til store og nøgle, og apksigners `file:`-læser kun giver ÉN
linje pr. forespørgsel):
```bash
source ~/android-build/env21.sh
PWF=~/android-build/husk-signing/.pw
bash ~/Tools/vault2/vault2.sh get-password "Husk release-signeringsnoegle (keystore husk-release.jks, base64)" | tr -d '
' > "$PWF"
chmod 600 "$PWF"
APK=~/android-build/husk-build/app/build/outputs/apk/release/app-release-unsigned.apk
$ANDROID_HOME/build-tools/34.0.0/apksigner sign   --ks ~/android-build/husk-signing/husk-release.jks --ks-key-alias husk   --ks-pass "file:$PWF" --out ~/android-build/husk-build/husk-vX.apk "$APK"
shred -u "$PWF"
$ANDROID_HOME/build-tools/34.0.0/apksigner verify --print-certs ~/android-build/husk-build/husk-vX.apk
```
eller via helper-scriptet:
`HUSK_KEYSTORE=~/android-build/husk-signing/husk-release.jks HUSK_KEY_ALIAS=husk bash gradle-build.sh`
(kodeordet gives via `HUSK_KS_PASS`, hentet fra vaulten - aldrig som literal).

---

## 6. Per-release vedligehold (checkliste)

Når en ny version udgives, skal følgende holdes i sync (ellers fejler in-app-update eller
F-Droid-CI):

1. Bump `versionCode` + `versionName` i `app/build.gradle`.
2. Byg + signér (afsnit 4–5).
3. Opdater repo'ets `latest.json` + `husk-latest.apk` (in-app-update henter dem via
   raw.githubusercontent / ISRG-cert – se [[husk-app]] for cert-historikken).
4. Opdater xplat HUSK-konstanter i `P_xplat/hosting/app.py` (`HUSK_VERSION_NAME`,
   `HUSK_VERSION_CODE`, `HUSK_APK` = raw `husk-latest.apk`) **OG DEPLOY xplat.co** (`P_xplat`:
   `check-local.sh` grøn → kør fra **Git Bash**: `bash scripts/hosting-deploy.sh --apply`.
   IKKE via `wsl.exe`: `vault2` kan kun køre i Git Bash, og scriptet henter selv
   Asura-nøglen gennem `scripts/deploy-asura/wsl-transport.sh`. Rettet 2026-09-04 efter at
   den gamle ordlyd fik en runde til at melde deployet umuligt)
   **+ verificér live**: `curl https://xplat.co/husk/latest.json` skal vise den nye `versionCode`.
   KRITISK: appens updater spørger xplat.co FØRST (GitHub-raw er kun fallback) – glemmer man at
   deploye xplat, siger enheder der nåer xplat "allerede nyeste" (set 0.9.18–0.9.21). Begge endpoints
   skal vise samme version.
4b. **API-dok-gate:** ajourfør `HUSK_API`-kataloget ved nye/ændrede endpoints/params/adgangsmodel, og
   kør `bash pc/check-api-parity.sh` (skal være grøn) – se §0b trin 6b. Opgaven er IKKE færdig før den.
5. GitHub-release med den signerede APK.
6. F-Droid-fork: opdater `fdroid/co.xplat.husk.yml` (MR !40810).
   - **Quote-regel for `CurrentVersion`/`versionName`:** numerisk-udseende (fx `0.9`, `0.2`)
     SKAL enkelt-quotes (`'0.9'`); ikke-numerisk (fx `0.9.1`, to punktummer) skal være
     **unquoted**. Dobbelt-quote eller forkert quoting får `fdroid rewritemeta`-CI til at fejle.
7. **Verificér F-Droid/GitLab-pipelinen grøn efter push** – se afsnit 7 for den fulde
   procedure (memory [[verify-ci-after-push]]).

---

## 7. Efter publicering: verificér F-Droid/GitLab-pipelinen (CI-tjek)

Dette er det tjek der **altid** skal laves efter en udgivelse, så vi aldrig efterlader en
failed pipeline. Stående regel: erklær aldrig "færdig" før pipelinen er grøn (memory
[[verify-ci-after-push]]).

**Kontekst.** F-Droid-indsendelsen er MR **!40810** fra forken **hf16/f-droid** (branch
`co.xplat.husk`) ind i `fdroid/fdroiddata`. Repo'ets `fdroid/co.xplat.husk.yml` er KILDEN
som kopieres til `hf16/f-droid:metadata/co.xplat.husk.yml` på forken (notationen er
`repo:sti-i-det-repo`; den sti findes kun i forken, ikke i dette repo). GitLab kører F-Droids CI (bl.a.
`fdroid lint` + `fdroid rewritemeta`/checkupdates + build-recipe-checks) på hver push til
fork-branchen. Publiceringen køres via GitLab-API'et med curl (telefonen har hverken `glab`
eller `gh`; al publicering sker fra sessionen). **Token (persistent):** GitLab-PAT'et ligger efter
secrets-off-computer (2026-07-10) i **vaulten**, ikke længere i `C:\Users\hf198\Tools\gitlab\token.txt`
(mappen er tom). Hent det ved runtime: `bash ~/Tools/vault2/vault2.sh get 'tool: gitlab/token.txt'`
og grep `glpat-...` ud – hardcode aldrig værdien. (GitLab-SSH-nøglen `gitlab_husk_ed25519` ligger
tilsvarende i ssh-agenten/vaulten.) Det er bevidst persistent, så både denne og en anden
session/bruger kan køre verifikationen uden at bede om et nyt token hver gang.

**Fork-opdatering uden lokal clone (verificeret 2026-07-11):** fdroiddata-forken er for stor at klone;
opdatér `hf16/f-droid:metadata/co.xplat.husk.yml` direkte via GitLab commits-API
(`POST /projects/hf16%2Ff-droid/repository/commits` med `actions:[{action:update, file_path, content}]`,
branch `co.xplat.husk`), og poll `GET /projects/.../pipelines?ref=co.xplat.husk` til `success`. Kør
via `py -3.11` + `urllib` (git-bash `python3` er Store-stubben).

**Sådan verificeres pipelinen (det jeg gjorde hver gang):**
```bash
TOKEN="$(grep -oE '^glpat-[A-Za-z0-9_.-]+' $HOME/Tools/gitlab/token.txt | head -1)"
FORK="hf16%2Ff-droid"        # url-encoded projekt-sti (eller brug det numeriske projekt-id)
BR="co.xplat.husk"
H="PRIVATE-TOKEN: $TOKEN"
# 1) seneste pipeline på fork-branchen:
curl -s --header "$H" "https://gitlab.com/api/v4/projects/$FORK/pipelines?ref=$BR&per_page=1"
#    -> {id, status}.  status: created|pending|running -> success|failed
# 2) poll til den er success/failed:
PID=<pipeline-id>
curl -s --header "$H" "https://gitlab.com/api/v4/projects/$FORK/pipelines/$PID" | grep -o '"status":"[a-z]*"'
# 3) ved failed -> find det fejlende job + læs traces:
curl -s --header "$H" "https://gitlab.com/api/v4/projects/$FORK/pipelines/$PID/jobs"
curl -s --header "$H" "https://gitlab.com/api/v4/projects/$FORK/jobs/<JOB_ID>/trace"
```

**Den hyppigste fejl = `fdroid rewritemeta`-quoting** af `CurrentVersion` / `versionName`:
- **Numerisk-udseende** version (fx `0.9`, `0.2`) → SKAL **enkelt-quotes**: `'0.9'`.
- **Ikke-numerisk** version (fx `0.9.1`, `0.1.5` – flere punktummer) → skal være **UNQUOTED**.
- Dobbelt-quote (`"0.2"`) eller forkert variant får `rewritemeta`-CI til at fejle.
- Fix metadata → re-commit til fork-branchen → poll igen til grøn.
  (ASCII-ificér aldrig dansk i selve app-strenge – men det er irrelevant for fdroid-yml'en,
  som er ren ASCII.)

**Når metadata ændres, hold disse i sync** (ellers fejler CI eller in-app-update):
1. `fdroid/co.xplat.husk.yml` i repo'et: tilføj `Builds`-entry (versionName/versionCode/`commit: vX`)
   og bump `CurrentVersion` + `CurrentVersionCode`.
2. Kopiér samme indhold til `hf16/f-droid:metadata/co.xplat.husk.yml` på forken og push til
   `co.xplat.husk`.
3. Opdater MR !40810 (note/beskrivelse) så maintaineren ser den nye version.
4. Poll pipelinen til **success**.

**Øvrige post-publish-tjek** (ikke GitLab, men hører til samme runde):
- GitHub-tag `vX` skal pege på den committede kilde (F-Droid bygger fra `commit: vX`).
- In-app-update: `https://raw.githubusercontent.com/hf1985/husk/main/latest.json` +
  `husk-latest.apk` skal være live og matche den nye `versionCode` (ISRG-cert → Android-9-OK).
- xplat: `https://xplat.co/husk/latest.json` opdateret (HUSK-konstanter i `P_xplat`).

## 7b. Reproducerbar build mod F-Droid (indført 2026-09-03, MR !40810)

`metadata/co.xplat.husk.yml` bærer nu `Binaries` + `AllowedAPKSigningKeys`. F-Droid bygger derfor
fra kilden, sammenligner med VORES udgivne APK, og distribuerer **vores egen signerede fil** i
stedet for at signere med deres nøgle. Det er hele grunden til at en F-Droid-installeret Husk kan
tage en in-app-opdatering: signaturerne matcher.

Det koster tre HÅRDE krav pr. release. Brækker et af dem, fejler F-Droids build:

1. **GitHub-releasen SKAL findes, og assetet SKAL hedde `husk-v<versionName>.apk`.**
   `Binaries: https://github.com/hf1985/husk/releases/download/v%v/husk-v%v.apk`; `%v` er
   versionName (fdroidserver `build.py`: `url.replace('%v', build.versionName)`).
   0.9.30 blev tagget UDEN GitHub-release, og `Binaries` havde derfor intet at hente, før det
   blev rettet 2026-09-03.
2. **Signeringsnøglen skal matche `AllowedAPKSigningKeys`.** Feltet er en LISTE
   (`build.py`: `used_key not in expected_keys`), så et nøgleskifte kan i princippet rummes ved
   at tilføje det nye fingeraftryk. Men Android accepterer stadig ikke en opdatering med skiftet
   signatur, så et skifte koster en afinstallation på hver enhed uanset hvad F-Droid gør.
3. **Byg release-APK'en med `assembleRelease` fra en ren kilde.** Kontrolmålingen er:
   klon taggen, byg, pak begge APK'er ud, og `diff -r` alt undtagen `META-INF/*.SF`, `*.RSA` og
   `MANIFEST.MF`. Er den tom, vil F-Droids verifikation også lykkes. Målt grøn på HFs-lenovo
   2026-09-03 mod den udgivne 0.9.30.

**Den fælde der kostede en runde:** AGP 8.5.2 skrev `META-INF/version-control-info.textproto` ind
i release-APK'en, og filens indhold afhænger af OM builden kørte i et git-checkout. Vores release
bygges i en trækopi uden `.git` og fik `generate_error_reason: NO_SUPPORTED_VCS_FOUND`; F-Droids
builder bygger I et checkout og fik en `repositories { system: GIT, revision: ... }`-blok. Det var
den ENESTE forskel mellem de to APK'er (GitLab-job 16290645645). Midlertidigt løst med
`rm: [.git]` i opskriften (job 16290790703 blev grøn); **permanent løst i 0.9.31** med
`vcsInfo { include false }` på release-buildTypen, hvorefter filen slet ikke findes og `rm`-linjen
er unødvendig.

## 8. Gotchas (lært undervejs)

- **WSL kan ikke chdir til en `/g`-cwd** → kald altid `wsl.exe --cd '~'` og brug absolutte
  `/mnt/g/...`-stier (memory [[ps-wsl-quoting]]). Kald wsl fra Bash-toolet (git-bash), ikke
  PowerShell – PowerShell sluger output ved embedded quotes/redirects.
- **Byg ikke direkte på `/mnt/g`** – Gradle på Google Drive-mountet er langsomt og upålideligt
  (`/mnt/g`-læsninger kan endda give "No such device" midt i en kørsel). Synk til WSL-lokalt
  først (det gør `gradle-build.sh`).
- **`org.gradle.jvmargs` tvinger en single-use daemon** (se loggens første linje) – uskadeligt.
  Vi kører `--no-daemon` i scriptet for ren engangs-build.
- Første build henter Gradle 8.7-distributionen + AGP – kan tage adskillige minutter. Med varm
  cache er en rebuild typisk ~1–2 min (mange `FROM-CACHE`).
- `apksigner` kræver JDK på PATH (`source env21.sh` først), ellers `exec: java: not found`.
- Multi-enheds-verifikation findes: WSL-emulatorer `husk26` (Android 8) + `husk34` (Android 14)
  i samme SDK – se [[husk-app]] (afsnit "GRATIS multi-enheds-test").

---

## 9. Filer i denne build-kæde

- `gradle-build.sh` – kanonisk build-helper (dette dokuments hovedværktøj).
- `build.sh` – on-phone ecj/dx dev-build (Termux).
- `setup.sh` – on-phone build + install + provisionering (Termux).
- `build.gradle`, `settings.gradle`, `gradle.properties`, `app/build.gradle` – Gradle-config.
- `gradle/wrapper/*` – committet wrapper (Gradle 8.7).
- `fdroid/co.xplat.husk.yml` – F-Droid-metadata (KILDE for fork-metadata, MR !40810).
- WSL: `~/android-build/` (miljø), `~/android-build/hb*.log` (historiske build-logs).
- **Signeringsnøgle (IKKE i git):** kanon er `husk-release.jks` i **vaulten** (login-item
  »Husk release-signeringsnoegle«), arbejdskopi `~/android-build/husk-signing/husk-release.jks`.
  Den PENSIONEREDE debug-keystore ligger stadig på telefonen (`~/husk/debug.keystore`) og i
  WSL, og skal blive der indtil hver enhed er geninstalleret på den nye nøgle. Se afsnit 5.
