# P_app_husk – fortsæt her

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver xplat): den publicerede
FOSS-app der gør en gammel Android-telefon til fjernstyret kamera plus
accessibility-automationsmotor plus scrcpy/adb-bro over eget Tailscale-net, uden
root. Overblik: `README.md`. Agent-kontekst, invarianter og release-pligten:
`CLAUDE.md` – **læs release-blokken øverst i den før du rører app-koden**.

## Status

**Udgivet og i drift.** Nuværende version **0.9.31 / versionCode 50**
(2026-09-03: ny release-signeringsnøgle + `vcsInfo { include false }`; ingen
adfærdsændring). Udgives på F-Droid (fdroiddata-MR !40810), GitHub-releases
(`hf1985/husk`) og `xplat.co/husk`. Pure framework, ingen AndroidX, ingen
afhængigheder.

**Signeringsnøglen blev skiftet i 0.9.31.** Den gamle var en genbrugt
debug-keystore hvis kodeord stod i klartekst i `docs/BUILD.md` i dette
OFFENTLIGE repo; F-Droid pinner nøglen permanent, så vinduet var FØR første
publicering. Ny nøgle: `CN=xplat`, RSA 4096, SHA-256 `96195cfd…c17d`, i
vaulten (se `docs/BUILD.md` §5). **Følgen er at in-app-updateren IKKE kan bære
springet fra 0.9.30 til 0.9.31** – Android afviser en signaturændring. Hver
enhed skal afinstalleres og geninstalleres via adb.

Flåden er tre fysiske enheder: Note10+ SM-N975U1 (Android 12, DeX, token,
`.103.102`) plus to spares, Sony 702SO (A9, tokenløs, `.101.101`) og Samsung
SM-A102U1 (A11, tokenløs, `.101.102`).

Husk er **motoren**; `husk-overbygning`, `P_app_phone-devbox` og `P_kontor`
bygger ovenpå, og `P_add-on_phone-transport` er hvordan man når telefonen.

## Det der oftest går galt, og som skal stå her

**En kodeændring er IKKE færdig før den er UDGIVET.** Committet-men-ikke-udgivet
betyder at de fysiske telefoner stadig kører den gamle kode. Det er sket:
XSS-fixet `e999ae4` blev committet og aldrig releaset, så alle enheder kørte
videre på 0.9.25 uden det.

**Updateren spørger `https://xplat.co/husk/latest.json` FØRST** (GitHub-raw er
kun fallback). Glemmes xplat.co-deployen, svarer enhederne »allerede nyeste« selv
om GitHub er opdateret. **Begge** endpoints skal vise den nye `versionCode`.

Den fulde otte-trins release-huskeliste står i `CLAUDE.md`; gentag den ikke her.
Definition af færdig: begge `latest.json`-endpoints viser den nye version,
F-Droid-pipelinen er grøn, **og** `pc/check-api-parity.sh` er grøn med `/husk/api`
ajourført.

## Verificeret / ikke verificeret

- **Fire ydelses-invarianter (A-D) må ALDRIG rulles tilbage.** De blev alle
  indført efter konkrete CPU- og frys-regressioner: doven skærm-streaming, smal
  a11y-maske, dovent kamera uden eviction, og **ingen cross-display launch**
  (invariant D er selv en fjernelse – v0.9.21's display-0-bounce triggede
  Samsungs »genstart på anden skærm«-dialog og crashede scrcpy og Discord på
  DeX). Læs `docs/YDELSE-OG-DRIFT.md` før du rører Screen, Camera eller a11y.
- **Spares kan itereres fuldt** – det tidligere »umuligt« var en fejldiagnose.
  En idle spare sover skærmen, så a11y kun ser navbaren og gestus svarer
  `ERR cancelled`; det blev læst som en død motor. **Kuren er `wake` FØRST.**
  Harness: `pc/spare.ps1` og `pc/spare.sh`.
- **Node-læsning er pålidelig på A11 og flaky på A9** – begge spares drives
  derfor via syn plus koordinat-tap, ikke via node-opslag.
- **Rest-gab:** en ren `adb reboot` på en spare kræver stadig et engangs-USB-kabel.
  Det er ikke nødvendigt for iteration. Fuld analyse:
  `docs/fleet-tailnet-transport.md`.

## Næste skridt

**1. De to spares skal på 0.9.31** (Hans tager dem; Note10 er gjort 2026-09-03).
Opskriften der virkede på Note10, i rækkefølge, alt via `adb` fra Termux:
afinstaller, installer, og **genskab så det afinstallationen tager med sig** –
det er mere end man tror:
- **Køretids-tilladelser nulstilles.** `/snapshot` svarede 503 »no frame yet«
  indtil `pm grant co.xplat.husk android.permission.CAMERA` (samt
  `RECORD_AUDIO`, de to `*_LOCATION`, `POST_NOTIFICATIONS`).
- **a11y-registreringen ryddes.** `settings put secure
  enabled_accessibility_services co.xplat.husk/co.xplat.husk.RigAccessibilityService`
  + `accessibility_enabled 1`, og **den binder først ved næste reboot**.
- **Batteri-undtagelsen ryddes.** `dumpsys deviceidle whitelist +co.xplat.husk`.
- **`dex_reconnect` og `screen_share` ryddes.** Den første kan sættes hovedløst
  (`am start -n co.xplat.husk/.MainActivity --ez dexreconnect true --ez finish true`);
  den anden kan IKKE, fordi `ScreenConsentActivity` er `exported="false"` – den
  kræver ét tryk på skærm-toggle i appens UI.
- **Tokenet overlever** (det bor i `Settings.Global husk_token`, ikke i app-prefs).
- **adb skal gå DIREKTE til adbd**, ikke gennem Husks bro på 15557: broen er en
  del af appen og dør i det sekund man afinstallerer. Find porten med
  `adb connect 127.0.0.1:15557` FØR afinstallationen, eller efter reboot når
  a11y har genrejst WD.

**2. `screen_share` mangler på Note10.** Skærmdeling var slået til før
udskiftningen og er det ikke nu. Ét tryk i appens UI.

**3. xplat.co ER deployet** (2026-09-04). Begge `latest.json`-endpoints viser
`versionCode 50`, `/husk/openapi.json` melder 0.9.31 med 44 paths, og
`pc/check-api-parity.sh` er grøn. Release-pligtens trin 8 er dermed opfyldt.

> **Fælde værd at huske:** deployet blev først fejlagtigt meldt umuligt, fordi
> `~/.ssh/agent.env` ikke fandtes i WSL. Det er den forkerte prøve.
> `hosting-deploy.sh` skal køres fra **Git Bash** (vault2 virker kun dér) og
> bruger selv `scripts/deploy-asura/wsl-transport.sh` som bro til den private
> Asura-nøgle, der kun ligger i WSL (`~/.ssh/khfrb_asura_openssh`).
> Én negativ prøve på ét sted er ikke et bevis for manglende adgang.

**Deploy til den kørende rig:** `adb install -r <apk>` når adb eller WD er sund,
derefter `adb reboot` for en ren fuld tilstand. **Launch aldrig `MainActivity`
via `am start` på den kørende rig** – det forgrunder Husk nær DeX og slår a11y
midlertidigt fra. Verificér i stedet via `/flags`.


