<!-- RFP (Request For Packaging) til F-Droid. Reference/skabelon til indsendelse paa
     https://gitlab.com/fdroid/rfp/-/issues/new (vaelg RFP-templaten).
     STATUS: pakningen er ALLEREDE i gang via fdroiddata-MR !40810 (se docs/BUILD.md afsnit 7);
     denne fil holdes blot ajour som menneskelaesbar reference. Den kanoniske build-metadata er
     fdroid/co.xplat.husk.yml. -->

### App information

- **Name:** Husk
- **Application ID:** `co.xplat.husk`
- **Source code:** https://github.com/hf1985/husk
- **License:** GPL-3.0-or-later
- **Latest version:** 0.9.21 (git tag `v0.9.21`)
- **Website:** https://xplat.co/husk

### Summary

Turn a spare Android phone into a remote camera and control rig over your own Tailscale network (no root).

### Description

Husk turns a spare Android phone into a remote camera and control rig on your own private
network (Tailscale), without root:

- Camera streaming (MJPEG) viewable in a browser or usable as a webcam via a PC virtual camera.
- An accessibility automation engine for hands-free, scripted on-screen actions on any display (incl. Samsung DeX).
- In-app recovery of Wireless Debugging after a reboot.
- An app-native adb bridge so a PC can mirror/control the phone with scrcpy (one-time pairing via the in-app endpoint).
- Optional "keep Samsung DeX connected" toggle (shown only on DeX-capable phones).
- Starts automatically after a reboot. Bilingual (Danish/English).

### Packaging notes

- **Pure framework app: no AndroidX, no proprietary dependencies, no trackers, no ads.**
- Builds with Gradle (Android Gradle Plugin 8.5.2 / Gradle 8.7 / JDK 17): `./gradlew assembleRelease`
  in subdir `app` (compileSdk 34, minSdk 26, targetSdk 34). The build has been verified.
- A ready-to-use fdroiddata metadata file is included in the repo at `fdroid/co.xplat.husk.yml`.
- **Sensitive capabilities (intentional, documented):** an AccessibilityService (for on-screen
  automation) and an adb bridge. Network services listen on all interfaces but are gated by a
  source-IP allowlist (only loopback, RFC1918/LAN and Tailscale peers; an optional token adds a
  second layer); nothing is sent to any external server except the user-configured motion push
  (their own ntfy topic). Restrict remote access further with a Tailscale ACL.
