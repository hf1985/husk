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
    // Skaerm-stream tuning (live-justerbar via /set?sq=..&sfps=..). Lavere kvalitet + hoejere fps = mindre
    // lag; dial ned ved smal baandbredde. Default = glat balance (~16 fps, JPEG q50).
    public static volatile int    screenQuality    = 50;   // JPEG-kvalitet 1..100
    public static volatile int    screenMinFrameMs = 60;   // capture-throttle (60ms ~= 16 fps)

    // DOVEN skaerm-produktion. ScreenService.onFrame springer den dyre Bitmap-alloc + JPEG-encode over
    // naar INGEN /screen(-snapshot)-klient ser med (og bevaegelses-alarm er FRA). Uden en forbruger er
    // 16fps-kodningen ren spildt CPU - det var rig'ens dominerende 24/7-CPU-post (traaden screen-bg) der
    // sultede Discords video. Stemples (uptimeMillis, monotont) af ControlServer ved hver /screen-foresporgsel;
    // H.264 (/screen.mp4) har sin egen lazy start/stop og roeres ikke. Boot-default 0 = ingen klient = idle.
    public static volatile long   lastScreenClientMs = 0;
    public static final  int      SCREEN_IDLE_MS     = 4000;   // bliv ved ~4s efter sidste klient-tick (glat ved korte drop)

    // Kamera-config (kan saettes via control-endpoint /set). Rotation = JPEG_ORIENTATION i grader.
    public static volatile int     rotation = 0;       // 0|90|180|270
    public static volatile boolean flip     = false;   // horisontal spejling
    // false = bagkamera (default). Skriv den KUN via setUseFront(): valget er PERSISTERET, fordi
    // en bar static nulstilles af hver procesgenstart, og MY_PACKAGE_REPLACED genstarter processen
    // ved hver opdatering. Uden persistens satte en ny version selv kameraet tilbage paa bagsiden,
    // og PC-webcam-produktet saelger sideskift som en funktion. Fjern den ikke som "overfloedig tilstand".
    public static volatile boolean useFront = false;
    public static volatile int     targetFps = 10;     // oevre graense for MJPEG-afsendelse

    // DOVEN kamera (samme princip som skaerm-gaten): CameraService holder kun kamera-ENHEDEN aaben naar
    // noget faktisk forbruger Husks kamera-feed (/stream el. /snapshot inden for CAMERA_IDLE_MS) ELLER
    // motion er TIL. Ingen forbruger -> kameraet SLIPPES, saa en anden app (fx Discord-moedekameraet paa
    // samme enhed) kan bruge det uforstyrret. Husk EVICTER ALDRIG en anden app (aabner kun naar ledigt).
    // Det var roden til at kameraet "frees" naar man aabnede/lukkede Husk-appen ved siden af Discord.
    // Stemples (uptimeMillis, monotont) af ControlServer ved /stream(-loop) + /snapshot.
    public static volatile long    lastCameraClientMs = 0;
    public static final  int       CAMERA_IDLE_MS     = 4000;   // bliv ved ~4s efter sidste klient-tick

    // Delt token. Tom = ingen token sat (kun kilde-IP-ACL'en beskytter da: enhver peer paa det
    // private net/Tailscale kan styre telefonen). Skriv den KUN via setToken().
    public static volatile String token = "";

    // Fra 1.4 er appens prefs ("husk"/"token") den ENESTE kilde til tokenet. Indtil 1.3 kunne det
    // ogsaa saettes med adb (en global systemindstilling) og via et intent-extra; begge veje er fjernet
    // med vilje uden migrering (ejerbeslutning 2026-09-24): en butiks-app skal kunne saettes op af
    // brugeren selv, i appen eller via /token/request med godkendelse paa telefonen. To kilder til
    // eet token gav desuden en tilstand hvor feltet og den virkelige vaerdi kunne vaere uenige.
    static final String KEY_TOKEN = "token";
    static final int TOKEN_MIN_LEN = 24;
    static final int TOKEN_MAX_LEN = 128;
    static final int TOKEN_GEN_LEN = 32;
    private static final String TOKEN_ALFABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // Gyldigt at GEMME: tomt (= slaa token fra) eller alfanumerisk med TOKEN_MIN_LEN..TOKEN_MAX_LEN tegn.
    // Alfanumerisk fordi tokenet reflekteres i /control-HTML'en og sendes i URL'er uden kodning.
    public static boolean tokenValid(String t) {
        if (t == null) return false;
        if (t.isEmpty()) return true;
        if (t.length() < TOKEN_MIN_LEN || t.length() > TOKEN_MAX_LEN) return false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'))) return false;
        }
        return true;
    }

    public static String generateToken() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder b = new StringBuilder(TOKEN_GEN_LEN);
        for (int i = 0; i < TOKEN_GEN_LEN; i++) b.append(TOKEN_ALFABET.charAt(rnd.nextInt(TOKEN_ALFABET.length())));
        return b.toString();
    }

    // Den ENE skrivevej (UI-feltet og token-API'et). Virker straks: ControlServer.tokenOk laeser
    // Rig.token ved hver request, saa ingen genstart kraeves. Returnerer false ved en ugyldig vaerdi.
    public static synchronized boolean setToken(android.content.Context c, String t) {
        if (!tokenValid(t)) return false;
        token = t;
        try {
            if (c != null) c.getSharedPreferences("husk", android.content.Context.MODE_PRIVATE).edit()
                            .putString(KEY_TOKEN, t).commit();
        } catch (Throwable ignored) {}
        return true;
    }

    // FAKTISK koerende-tilstand (ikke bare den gemte toggle) - status/UI/flags SKAL vise sandt.
    // cameraRunning saettes af CameraService naar capture reelt koerer; screenRunning af ScreenService.
    public static volatile boolean cameraRunning = false;
    public static volatile boolean screenRunning = false;


    // Proces-singleton HTTP-server (8090), AFKOBLET fra CameraService saa skaermdeling + /control
    // virker UDEN kameraet. Een instans pr. proces; startes idempotent af hvilken-som-helst service.
    public static volatile ControlServer controlServer = null;
    public static synchronized void ensureControlServer() {
        if (controlServer == null) {
            ControlServer cs = new ControlServer();
            cs.start();
            controlServer = cs;
        }
    }

    // ---- Hardware-accelereret skaerm-stream (H.264 -> fMP4 -> MSE) ----
    // ScreenService eksponerer sin MediaProjection + downscalede capture-dims her, saa H264Stream kan lave sit
    // EGET VirtualDisplay paa samme projection. H.264 startes LAZILY (foerste /screen.mp4-klient) + stoppes naar
    // ingen klienter er tilbage. Opt-in -> MJPEG-/control roeres ikke. Kraever at skaermdeling (ScreenService) er paa.
    public static volatile android.media.projection.MediaProjection mediaProjection = null;
    public static volatile int capW = 0, capH = 0, capDpi = 0;   // downscalede capture-dims (= H.264-stoerrelse)
    public static volatile H264Stream h264 = null;
    public static synchronized H264Stream ensureH264() {
        if (h264 != null) return h264;
        if (mediaProjection == null || capW <= 0 || capH <= 0) return null;   // skaermdeling ikke aktiv
        try { H264Stream s = new H264Stream(mediaProjection, capW, capH, capDpi); s.start(); h264 = s; return s; }
        catch (Throwable t) { return null; }
    }
    public static synchronized void stopH264() {
        if (h264 != null) { try { h264.stop(); } catch (Throwable ignored) {} h264 = null; }
    }

    // App-Context (sat af services i onCreate) saa hardware/info-endpoints virker UDEN at a11y er oppe.
    public static volatile android.content.Context appContext = null;

    // Bedste tilgaengelige Context: app-context hvis sat, ellers a11y-servicen (selv en Context), ellers null.
    public static android.content.Context ctx() { return appContext != null ? appContext : a11y; }

    // Reference til den forbundne a11y-service (sat i onServiceConnected). null = a11y ikke aktiveret.
    public static volatile RigAccessibilityService a11y = null;

    // Sidst kendte WD ip:port fra recovery (cache til /wd naar skaermen ikke kan laeses).
    public static volatile String lastWdIpPort = "";

    // DeX-reconnect-toggle (bruger-styret, kun relevant paa DeX-kapable enheder). Naar TIL sikrer
    // appen at DeX er oppe efter boot/recovery. Saettes fra SharedPreferences ved start; laeses af
    // /flags saa en evt. overbygning kan se den.
    public static volatile boolean dexReconnect = false;

    // --- Bevaegelses-alarm (motion-detection) -------------------------------------------------------
    // Killer-feature: goer enhver gammel telefon til et gratis, privat sikkerhedskamera der SELV holder
    // oeje og alarmerer (ntfy-push) ved bevaegelse. Sat fra SharedPreferences ved start + /motion-endpoint.
    public static volatile boolean motionEnabled = false;
    public static volatile String  ntfyServer = "https://ntfy.sh";   // konfigurerbar ntfy-server
    public static volatile String  ntfyTopic = "";                   // tom = ingen push (kun /events-log)
    public static volatile int     motionSensitivity = 5;            // 1..10 (10 = mest foelsom)
    public static volatile long    motionCooldownMs = 30000;         // min ms mellem alarmer pr. kilde
    public static volatile String  lastNtfy = "";                    // sidste push-resultat (til /flags)

    // Ring-buffer over seneste bevaegelses-haendelser (nyeste foerst), eksponeret som JSON via /events.
    public static final java.util.List<String> motionEvents =
            java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

    public static void addMotionEvent(long t, String src, int pct) {
        synchronized (motionEvents) {
            motionEvents.add(0, "{\"t\":" + t + ",\"source\":\"" + src + "\",\"change\":" + pct + "}");
            while (motionEvents.size() > 50) motionEvents.remove(motionEvents.size() - 1);
        }
    }

    public static void loadMotionPrefs(android.content.Context c) {
        try {
            android.content.SharedPreferences p = c.getSharedPreferences("husk", android.content.Context.MODE_PRIVATE);
            motionEnabled = p.getBoolean("motion_enabled", false);
            ntfyServer = p.getString("ntfy_server", "https://ntfy.sh");
            ntfyTopic = p.getString("ntfy_topic", "");
            motionSensitivity = p.getInt("motion_sensitivity", 5);
            useFront = p.getBoolean(KEY_USE_FRONT, false);   // kameraside overlever procesgenstart (se useFront)
            token = p.getString(KEY_TOKEN, "");                // eneste kilde fra 1.4 (se KEY_TOKEN)
        } catch (Throwable ignored) {}
    }

    // Kanalen er appens egne prefs, ikke en global systemindstilling: den kan appen kun LAESE
    // (skrivning kraever WRITE_SECURE_SETTINGS, som en butiks-app ikke har). Prefs overlever en
    // opdatering, men ikke en afinstallation - det er opdateringen fundet handlede om.
    static final String KEY_USE_FRONT = "use_front";

    public static void setUseFront(android.content.Context c, boolean front) {
        useFront = front;
        try {
            if (c != null) c.getSharedPreferences("husk", android.content.Context.MODE_PRIVATE).edit()
                            .putBoolean(KEY_USE_FRONT, front).apply();
        } catch (Throwable ignored) {}
    }

    public static void saveMotionPrefs(android.content.Context c) {
        try {
            c.getSharedPreferences("husk", android.content.Context.MODE_PRIVATE).edit()
             .putBoolean("motion_enabled", motionEnabled)
             .putString("ntfy_server", ntfyServer)
             .putString("ntfy_topic", ntfyTopic)
             .putInt("motion_sensitivity", motionSensitivity).apply();
        } catch (Throwable ignored) {}
    }
}
