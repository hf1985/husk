# P_app_husk – fortsæt her

**Husk** (`co.xplat.husk`, GPL-3.0-or-later, udgiver xplat): den publicerede
FOSS-app der gør en gammel Android-telefon til fjernstyret kamera plus
accessibility-automationsmotor plus scrcpy/adb-bro over eget Tailscale-net, uden
root. Overblik: `README.md`. Agent-kontekst, invarianter og release-pligten:
`CLAUDE.md` – **læs release-blokken øverst i den før du rører app-koden**.

## Status

**Udgivet og i drift.** Nuværende version **0.9.30 / versionCode 49** (release af
token-gate-fixet, 2026-07-16). Udgives på F-Droid (fdroiddata-MR !40810),
GitHub-releases (`hf1985/husk`) og `xplat.co/husk`. Pure framework, ingen
AndroidX, ingen afhængigheder.

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

Ingen bunden kodeopgave. Sporet nedenfor er den eneste åbne adressering af
projektet lige nu.

**Deploy til den kørende rig:** `adb install -r <apk>` når adb eller WD er sund,
derefter `adb reboot` for en ren fuld tilstand. **Launch aldrig `MainActivity`
via `am start` på den kørende rig** – det forgrunder Husk nær DeX og slår a11y
midlertidigt fra. Verificér i stedet via `/flags`.


