# CUTOVER 2026-06-18: note10-app afløser dextap som rig-motor

Den samlede `note10-app` overtog office-rig'ens motor-rolle fra `dextap` (com.khfrb.dextap).
Kørt autonomt mod den live rig (SM-N975U1) over Tailscale, off-hours efter 18:00-leave.

## Hvad blev gjort
1. **engine.rpc (8127) gen-tilføjet** til `RigAccessibilityService` (port af DexRPC `TapService`):
   byte-identisk linje-protokol (ping/displays/rotation/tap/swipe/find/click/state/gettext/dump/
   launch/scroll/global) -> appen er en **drop-in DexRPC-superset**. Bind med SO_REUSEADDR + retry
   (robust dextap->note10-handoff). Build on-phone (ecj/dx/aapt2), BUILD-OK.
2. **Funktion bekræftet live** (camera frit efter 18:00):
   - M1 kamera `/snapshot` -> 200; `/healthz` ok.
   - 8127 PONG som note10 (efter motorskifte); node-læsning paa DeX display 2 (`state 2 .`=1, `dump 2`).
   - **Fuld daglig cyklus paa note10-motoren:** `meeting-camera/join.sh` -> Discord launch display 2 ->
     Join Voice -> kamera-til -> `setMode(MODE_IN_COMMUNICATION)`; `leave.sh` -> Disconnect. UAENDRET shell.
   - **Kamera-kontention afklaret:** Discord (foreground) **evicter** appens baggrunds-kamera -> appen
     blokerer IKKE mødet. Discord vinder kameraet som hidtil.
3. **Reboot-validering (hands-off):** `adb reboot` -> WD-port skiftede 35953->42047 (ægte reboot) ->
   note10-a11y bandt paa boot (8127 op) -> BootReceiver startede CameraService (HTTP+kamera, SAMME proces)
   -> `boot-start.sh`/`wd-up.sh` drev note10's 8127 til at tænde WD + adb reconnect. `/wd` -> ip:port. Alt hands-off.
4. **Cutover:** `dextap` afinstalleret (`pm uninstall com.khfrb.dextap`). note10 = eneste a11y/motor.
   Meeting-camera-overbygning + crontab (join 9 / self-check 9:05·12·14 / leave 18) + Termux:Boot UÆNDRET
   (de taler 8127 = nu note10).

## Arkitektur efter cutover
- **note10-app** (een APK, een proces): engine.rpc 8127 (a11y) + CameraService (HTTP 8090: /snapshot /stream
  /wd /set /) + BootReceiver. = det generiske produkt-fundament.
- **Overbygning** = `~/meeting-camera` shell (join/leave/self-check/wd-up/boot-start), uændret, paa 8127.
- **PC-companion** (M5, scrcpy): **app-native + Termux-uafhængig** (se nedenfor).

## scrcpy: app-native adb-bro (TILFØJET 2026-06-18, efter cutover)
scrcpy var oprindeligt afhængig af en Termux socat-forward (5037/27183) + en Termux:Boot-hook, som
brød ved reboot (hooken pegede på den nedlagte `~/discord-bot`-sti efter splittet). Det er nu lagt
**ind i appen**, Termux-uafhængigt:
- `AdbForward` (ny Java-klasse) broer **Tailscale-IP:5557 -> 127.0.0.1:<WD-port>** = eksponerer enhedens
  egen adbd over Tailscale. Startet af `ControlServer` (CameraService). WD-porten skifter ved reboot;
  broen henter den fra appens in-process WD-recovery (`Rig.lastWdIpPort`).
- `/pair`-endpoint: a11y åbner WD "Pair device with pairing code"-dialogen + læser kode+port, så en
  frisk PC kan parres hovedløst (Android 12 WD = TLS → engangs-parring pr. PC; persisterer over reboot).
- `pc/note10-companion.ps1`: fresh-Win11 PowerShell-companion. Bootstrapper scrcpy via winget, parrer via
  `/pair`, `adb connect <ts>:5557`, laver de to skrivebordsgenveje (skærm + DeX/TV). Ingen WSL/ssh/socat.
- **Bevist live + reboot-fast:** efter `adb reboot` kom broen op hands-off (ny WD-port 36609 auto-fundet),
  PC'en `adb connect`ede UDEN gen-parring, og scrcpy spejlede. Ingen socat-forwardere kørte. Den gamle
  Termux:Boot-hook `30-adb-tailscale-forward.sh` er pensioneret (→ `~/.retired-boot-hooks/`).
- Udfaset: `pc/note10-connect.sh` (WSL-vej) + `remote-control`'s socat-forward + dens scrcpy-genveje.

## Rollback (hvis morgendagens 09:00-møde fejler)
dextap-kilden er bevaret: telefonens `~/dexrpc` (+ `~/dextap-dev`, `~/discord-bot.manual-bak-20260605`),
Drive `P_app_DexRPC`, GitHub `hf1985/DexRPC`. Hurtig rollback:
```
ssh note10 'cd ~/dexrpc && bash setup.sh'   # genbyg+install dextap, gen-aktiver dens a11y additivt
# note10's 8127 viger (SO_REUSEADDR-race) eller deaktiver note10-a11y for at give dextap 8127.
```
self-check.sh (09:05, SMTP-notify) er den indbyggede alarm hvis kameraet ikke er aktivt om morgenen.

## Residual-risiko (overvåg)
- **a11y-rebind ved proces-kill midt på dagen:** ved ren boot binder a11y rent (verificeret). En `install -r`
  midt i drift efterlod a11y u-bundet et øjeblik i testen. Et system-kill (Doze/RAM) bør gen-binde a11y
  normalt (services genstartes; partial wakelock + batteri-whitelist mindsker kills), men er ikke soak-bevist.
  Hvis 8127 nogensinde tier midt på dagen: toggle accessibility_enabled, eller reboot.

## Endnu ikke gjort (bevidst deferreret)
- **DeX-rejoin bruger-toggle + /flags** (PLAN §5): produkt-feature for DeX-løse telefoner. Office-rig'en
  vil altid DeX (håndteres af shellens `ensure_dex_up`), så ikke nødvendig for office-funktionen.
- **Slankning af overbygningens wd-up/boot-start** (PLAN §7.2): wd-up er nu redundant med appens in-process
  /wd, men UÆNDRET = bevist. Behold til den nye stak har kørt et rigtigt 09:00-møde; slank derefter.
- **Fjern rollback-net på telefon** (`~/dexrpc`, `~/dextap-dev`, `~/discord-bot.manual-bak-20260605`): fjern
  efter første grønne 09:00-møde på note10. (`socat` findes -> lib.sh's inline `dex`-fallback virker uden dexrpc.)
- **note10-app som eget GitHub-repo** (PLAN §9.7): afventer bruger-go (jf. økosystem-split "gør public/sælg").
