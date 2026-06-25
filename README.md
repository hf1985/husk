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
layer). Nothing leaves your device except the motion push you configure (your own ntfy topic).
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

## Permissions (and why)
INTERNET (local HTTP + adb bridge, loopback/Tailscale only), CAMERA, FOREGROUND_SERVICE(_CAMERA),
RECEIVE_BOOT_COMPLETED, WAKE_LOCK, POST_NOTIFICATIONS, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, and the
AccessibilityService binding. These are the core of the app and are documented for transparency.

## License
GPL-3.0-or-later. See `LICENSE`.
