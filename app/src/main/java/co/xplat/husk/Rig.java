// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

// Delt, proces-global tilstand for den samlede Husk-rig-app. Alt koerer i een proces
// (a11y-service + kamera-service + HTTP-server), saa en lille statisk holder er den
// simpleste lim (constraint: faerrest moving parts). Felterne er volatile fordi de
// skrives/laeses paa tvaers af kamera-, server- og main-traade.
public final class Rig {
    private Rig() {}

    // Seneste JPEG-frame fra kameraet (raa bytes, klar til MJPEG/snapshot).
    public static volatile byte[] latestJpeg = null;
    public static volatile long   latestSeq  = 0;     // taeller, saa /stream kun sender nye frames

    // Seneste JPEG-frame fra SKAERMEN (MediaProjection, ScreenService). Workaround til scrcpy paa
    // enheder uden Wireless Debugging (fx Android 9): se skaermen i browseren over Tailscale, og
    // styr den med klik->a11y-tap. screenW/H = RIGTIGE skaerm-px (til klik-koordinat-mapping).
    public static volatile byte[] latestScreenJpeg = null;
    public static volatile long   latestScreenSeq  = 0;
    public static volatile int    screenW = 0, screenH = 0;

    // Kamera-config (kan saettes via control-endpoint /set). Rotation = JPEG_ORIENTATION i grader.
    public static volatile int     rotation = 0;       // 0|90|180|270
    public static volatile boolean flip     = false;   // horisontal spejling
    public static volatile boolean useFront = false;   // false = bagkamera (default)
    public static volatile int     targetFps = 10;     // oevre graense for MJPEG-afsendelse

    // Delt token. Tom = ingen token sat (kun loopback+Tailscale-bind beskytter da, jf. ACL-laget).
    public static volatile String token = "";

    // Reference til den forbundne a11y-service (sat i onServiceConnected). null = a11y ikke aktiveret.
    public static volatile RigAccessibilityService a11y = null;

    // Sidst kendte WD ip:port fra recovery (cache til /wd naar skaermen ikke kan laeses).
    public static volatile String lastWdIpPort = "";

    // DeX-reconnect-toggle (bruger-styret, kun relevant paa DeX-kapable enheder). Naar TIL sikrer
    // appen at DeX er oppe efter boot/recovery. Saettes fra SharedPreferences ved start; laeses af
    // /flags saa en evt. overbygning kan se den.
    public static volatile boolean dexReconnect = false;
}
