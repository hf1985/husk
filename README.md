<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Husk

Turn a spare Android phone into a remote **camera and control rig** on your own private
[Tailscale](https://tailscale.com) network, without root. Husk is a single pure-framework
Android app (no AndroidX, no trackers, no ads) published as FOSS.

> Publisher: **xplat** · App ID `co.xplat.husk` · License **GPL-3.0-or-later** · PC companion: **xplat.co/husk**

## What it does
- **Camera streaming** (MJPEG) viewable in a browser or usable as a webcam in a meeting (via a PC virtual camera).
- **Automation engine** (AccessibilityService) for hands-free, scripted on-screen actions on any display (incl. Samsung DeX).
- **Wireless-Debugging recovery** in-app after a reboot.
- **App-native adb bridge** (Tailscale-IP:15557 -> the device's own adbd) so a PC can mirror/control the phone with `scrcpy` -- no Termux, no ssh, no socat. One-time pairing via the in-app `/pair` endpoint. (Port 15557 sits outside adb's 5555-5585 emulator-scan range, so a local adb server never adopts the bridge as a phantom "emulator-5556".)
- **DeX auto-reconnect** toggle (shown only on DeX-capable phones).
- **Starts after reboot** (boot persistence).

Network services listen on all interfaces but are gated by a source-IP allowlist (only
loopback, RFC1918/LAN and Tailscale `100.64.0.0/10` peers; an optional token adds a second
layer). Nothing leaves your device except two things you turn on or ask for yourself: the motion push
(to your own ntfy topic), and the update check, which reads version information from xplat.co
and downloads the APK from GitHub when you start an update.
Restrict remote access further with a Tailscale ACL.

## Simple UI
One screen: status, a few toggles (camera, DeX auto-reconnect, ...), one-tap deep-links to every
required system setting (Accessibility, Developer options / Wireless debugging, Battery optimisation,
App permissions), and a link to the PC companion. Bilingual: **Danish on Danish devices, English elsewhere.**

## Build
Standard Gradle project (Android Gradle Plugin 8.5.2, Gradle 8.7, JDK 17, compileSdk 34, minSdk 26).
Pure framework APIs only -- no dependencies.
```bash
./gradlew assembleRelease        # -> app/build/outputs/apk/release/app-release-unsigned.apk
```
The canonical/F-Droid build is Gradle. **Full, reproducible procedure (WSL build env, the
G:->WSL sync, signing key, per-release checklist):** see [`docs/BUILD.md`](docs/BUILD.md);
the one-command helper is [`gradle-build.sh`](gradle-build.sh). A convenience on-phone build
(`build.sh`, ecj/dx/aapt2 in Termux) exists for fast iteration on the device itself.

## Performance, resource invariants & operating a live rig
**Before changing `ScreenService` or the accessibility event mask, read
[`docs/YDELSE-OG-DRIFT.md`](docs/YDELSE-OG-DRIFT.md).** Husk runs idle most of the time and must
only burn CPU on demand. That doc captures two must-not-regress invariants (lazy screen encoding;
narrow a11y mask kept fresh only during recovery), a diagnostics playbook for finding CPU hogs on
the host (Termux `top` is blind to other UIDs -- use `adb shell dumpsys cpuinfo`), and the safe
deploy path on a device that shares the camera with another app (never foreground `MainActivity`
on a running rig -- it evicts the other app from the camera). Includes the v0.9.19 "Discord
stutter" postmortem.

## PC companion (scrcpy)
`pc/husk-companion.ps1` -- a PowerShell setup script for a fresh Windows 11 (bootstraps scrcpy via
winget, pairs the PC hands-free via `/pair`, creates desktop shortcuts). See **xplat.co/husk**.

## HTTP API (port 8090, loopback + Tailscale, optional `?token=`)
`/healthz` (open) · `/snapshot` · `/stream` (MJPEG) · `/wd` (turn on Wireless Debugging, read ip:port)
· `/pair` (WD pairing for a new PC) · `/flags` (read-only state) · `/set` (camera params) · `/` (viewer).
adb bridge on port **15557**. Automation RPC on loopback **8127**.

## Access token (since 1.4)
Other devices send the token as `?token=` to control the phone. There are two ways to set it:
- **In the app:** the *Access token* field. *Generate* makes a random 32-character token, *Copy*
  puts it on the clipboard, *Save* applies it right away (empty = no token).
- **From another device:** `GET /token/request?client=<name>` shows a notification on the phone;
  tap **Approve**, then poll `GET /token/status?id=<id>` (`pending`, `denied`, `expired`, or
  `approved` with the token, handed out once). If no token is set, approving sets one
  (`&new=<proposal>` if valid, otherwise generated). `GET /token/set?token=<current>&new=<new>`
  changes an existing token.

**A device without a token is open:** anyone on your Tailscale network can control it, and can
therefore also tap *Approve* on the phone themselves (through the accessibility API). The approval
only protects once a token is set. Such a peer can even set a token of its own choosing and lock
you out; clear or replace it in the app's field. Set a token before you share the network. Until 1.3 the token could be set with adb as a global system
setting; 1.4 no longer reads it, so set the token again after updating.

## Permissions (and why)
INTERNET (local HTTP + adb bridge, loopback/Tailscale only), CAMERA, FOREGROUND_SERVICE(_CAMERA),
RECEIVE_BOOT_COMPLETED, WAKE_LOCK, POST_NOTIFICATIONS, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, and the
AccessibilityService binding. These are the core of the app and are documented for transparency.

## Security
Husk's accepted-risk model, in one place:
- **8090 (HTTP)** binds all interfaces but is gated by a source-IP allowlist (loopback,
  RFC1918/LAN, Tailscale `100.64.0.0/10`) and an optional shared `?token=`. When no token is
  set, the IP allowlist is the only gate -- keep it tight (Tailscale ACL) on untrusted LANs.
  `/token/request` and `/token/status` are open by design; see *Access token* above.
  CSRF and DNS-rebinding defenses are applied to state-changing endpoints.
- **8127 (automation RPC)** binds **only** `127.0.0.1` -- never a network address -- so it is
  unreachable from other devices. It has no token or app-level auth: any other app installed
  on the same phone with `INTERNET` permission can reach it and tap/swipe/read all displays.
  This is accepted on a single-purpose rig with a controlled app set, where the remote attack
  surface is covered by the Tailscale ACL on 8090/15557. If the on-device app set becomes
  less trusted, add a shared token to the 8127 protocol before relying on it.
- **15557 (adb bridge)** requires one-time pairing via `/pair` and otherwise follows the same
  source-IP allowlist as 8090.
- Nothing leaves the device except the motion push you configure (your own ntfy topic).
  Since 1.1 there is no built-in updater and no update check: the app never downloads or
  installs anything on its own. Update it from F-Droid, or with `adb install`.

## License
GPL-3.0-or-later. See `LICENSE`.
