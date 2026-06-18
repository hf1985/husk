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
- **App-native adb bridge** (Tailscale-IP:5557 -> the device's own adbd) so a PC can mirror/control the phone with `scrcpy` -- no Termux, no ssh, no socat. One-time pairing via the in-app `/pair` endpoint.
- **DeX auto-reconnect** toggle (shown only on DeX-capable phones).
- **Starts after reboot** (boot persistence).

All network services bind ONLY to localhost + your Tailscale address (never `0.0.0.0`). Nothing
leaves your device. Restrict remote access with a Tailscale ACL.

## Simple UI
One screen: status, a few toggles (camera, DeX auto-reconnect, ...), one-tap deep-links to every
required system setting (Accessibility, Developer options / Wireless debugging, Battery optimisation,
App permissions), and a link to the PC companion. Bilingual: **Danish on Danish devices, English elsewhere.**

## Build
Standard Gradle project (Android Gradle Plugin 7.4.2, Gradle 7.6.x, compileSdk 33, minSdk 30).
Pure framework APIs only -- no dependencies.
```bash
./gradlew assembleRelease        # -> app/build/outputs/apk/release/app-release-unsigned.apk
```
A convenience on-phone build (`build.sh`, ecj/dx/aapt2 in Termux) exists for fast iteration on the
device itself; the canonical/F-Droid build is Gradle.

## PC companion (scrcpy)
`pc/husk-companion.ps1` -- a PowerShell setup script for a fresh Windows 11 (bootstraps scrcpy via
winget, pairs the PC hands-free via `/pair`, creates desktop shortcuts). See **xplat.co/husk**.

## HTTP API (port 8090, loopback + Tailscale, optional `?token=`)
`/healthz` (open) · `/snapshot` · `/stream` (MJPEG) · `/wd` (turn on Wireless Debugging, read ip:port)
· `/pair` (WD pairing for a new PC) · `/flags` (read-only state) · `/set` (camera params) · `/` (viewer).
adb bridge on port **5557**. Automation RPC on loopback **8127**.

## Permissions (and why)
INTERNET (local HTTP + adb bridge, loopback/Tailscale only), CAMERA, FOREGROUND_SERVICE(_CAMERA),
RECEIVE_BOOT_COMPLETED, WAKE_LOCK, POST_NOTIFICATIONS, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, and the
AccessibilityService binding. These are the core of the app and are documented for transparency.

## License
GPL-3.0-or-later. See `LICENSE`.
