// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

// Lille HTTP-server (raa ServerSocket, ingen lib) til den samlede rig. Den BINDER KUN til
// loopback + Tailscale-IP'en, ALDRIG 0.0.0.0 - praecis som remote-control/adb-tailscale-forward.sh.
// Fjernfladen daekkes saaledes af Tailscale-ACL'en + en delt token (?token=...). /healthz er
// aaben (liveness). Een traad pr. listener-adresse; hver forbindelse haandteres paa egen traad.
//
// Ruter:
//   GET /healthz                 -> "ok"                      (ingen token)
//   GET /snapshot?token=         -> seneste JPEG              (image/jpeg)
//   GET /stream?token=           -> MJPEG (multipart/x-mixed-replace)
//   GET /wd?token=               -> JSON {"ip":"..","port":N} (trigger WD-recovery, til PC-companion)
//   GET /set?token=&rot=&flip=&fps= -> "ok"                   (juster kamera-config i farten)
//   GET /                        -> minimal browser-viewer (overvaagning, M2)
public class ControlServer {
    static final String TAG = "Husk";
    static final int PORT = 8090;   // IKKE 8127/5037/27183/8022 - undgaar konflikt med rig-portene

    private final List<ServerSocket> listeners = new ArrayList<ServerSocket>();
    private volatile boolean running = false;
    private AdbForward adbForward;   // app-native scrcpy/adb-over-Tailscale (Termux-uafhaengig bro)

    public void start() {
        running = true;
        // EN listener paa 0.0.0.0 -> altid naabar paa loopback + LAN + Tailscale, uanset om Tailscale-IP'en
        // kan enumereres som interface-adresse. Foer bandt vi kun de enumererede private interfaces; naar
        // Android-Tailscale-tun'en blev rekonfigureret (IP-skift, reconnect, doze/wake, app-restart) forsvandt
        // Tailscale-IP'en fra enumereringen, saa 8090 var kun paa loopback/LAN -> "disconnected" paa alle tre.
        // 0.0.0.0 kan ikke ramme det. Sikkerheden er nu kilde-IP-ACL'en (allowedPeer) + valgfri token.
        bindAndServe("0.0.0.0");
        // adb-broen (scrcpy) binder ogsaa 0.0.0.0 + samme kilde-ACL, saa den ikke afhaenger af at kunne
        // enumerere Tailscale-IP'en (samme svaghed som 8090 havde). Startes een gang; WD-recovery er lazy.
        try { adbForward = new AdbForward(); adbForward.start(); } catch (Throwable t) { Log.e(TAG, "adb-bro start fejlede", t); }
        Log.i(TAG, "control-server lytter paa 0.0.0.0:" + PORT + " (kilde-ACL: kun loopback/privat/Tailscale)");
    }

    private void bindAndServe(final String host) {
        try {
            final ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(host), PORT), 16);
            listeners.add(ss);
            Thread th = new Thread(new Runnable() { public void run() { acceptLoop(ss, host); } }, "rig-http-" + host);
            th.setDaemon(true);
            th.start();
        } catch (Throwable t) {
            Log.e(TAG, "bind " + host + ":" + PORT + " fejlede", t);
        }
    }

    private void acceptLoop(ServerSocket ss, String host) {
        while (running) {
            try {
                final Socket c = ss.accept();
                if (!allowedPeer(c)) { try { c.close(); } catch (Throwable ignored) {} continue; }
                Thread th = new Thread(new Runnable() { public void run() {
                    try { handle(c); } catch (Throwable t) { /* klient lukkede */ }
                    finally { try { c.close(); } catch (Throwable ignored) {} }
                } }, "rig-conn");
                th.setDaemon(true);
                th.start();
            } catch (Throwable t) {
                if (running) Log.e(TAG, "accept(" + host + ")", t);
                else break;
            }
        }
    }

    // Kilde-IP-ACL: kun loopback + privat (RFC1918) + Tailscale (CGNAT 100.64/10, ULA fc00::/7) peers maa naa
    // serveren. Dette erstatter den gamle "bind kun private interfaces"-beskyttelse og er sikkert med 0.0.0.0-bind,
    // fordi det tjekker PEER-adressen (en offentlig kilde, fx paa mobildata, afvises uanset bind).
    private boolean allowedPeer(Socket c) {
        try {
            java.net.SocketAddress sa = c.getRemoteSocketAddress();
            if (!(sa instanceof InetSocketAddress)) return false;
            return Net.peerAllowed(((InetSocketAddress) sa).getAddress());   // delt ACL (loopback/RFC1918/Tailscale)
        } catch (Throwable t) { return false; }
    }

    // Een TCP-forbindelse, HTTP/1.1 keep-alive: vi laeser FLERE requests paa samme forbindelse i en loop, saa
    // hurtige /tap //swipe ikke betaler en TCP-handshake-RTT hver gang (stor forskel over Tailscale). Inaktive
    // forbindelser lukkes af SoTimeout. TCP_NODELAY (ingen Nagle) -> smaa tap-kvitteringer sendes straks.
    // Streaming (/stream //screen) koerer uendeligt og beslaglaegger forbindelsen -> de lukker den naar klienten gaar.
    private void handle(Socket c) throws IOException {
        c.setSoTimeout(20000);
        try { c.setTcpNoDelay(true); } catch (Throwable ignored) {}
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        OutputStream out = c.getOutputStream();
        while (running) {
            String reqLine;
            try { reqLine = r.readLine(); }
            catch (java.net.SocketTimeoutException e) { return; }   // inaktiv keep-alive -> luk
            if (reqLine == null) return;                            // klient lukkede forbindelsen
            String h; while ((h = r.readLine()) != null && h.length() > 0) { /* drain headers */ }
            String[] parts = reqLine.split(" ");
            if (parts.length < 2) { writeText(out, 400, "bad request"); return; }
            String path = parts[1], query = "";
            int q = path.indexOf('?');
            if (q >= 0) { query = path.substring(q + 1); path = path.substring(0, q); }
            // Streaming beslaglaegger forbindelsen (uendelig multipart / fMP4) -> luk efter (return).
            if (path.equals("/stream")) { writeStream(c, out); return; }
            if (path.equals("/screen")) { writeScreenStream(c, out); return; }
            if (path.equals("/screen.mp4")) { writeH264Stream(c, out); return; }   // hardware-H.264 -> MSE
            dispatch(out, path, query);   // alt andet svares keep-alive; loopen laeser naeste request
        }
    }

    private void dispatch(OutputStream out, String path, String query) throws IOException {
        if (path.equals("/healthz")) { writeText(out, 200, "ok"); return; }
        if (path.equals("/")) { writeText(out, 200, viewerHtml(), "text/html; charset=utf-8"); return; }

        if (!tokenOk(query)) { writeText(out, 401, "unauthorized"); return; }

        // --- status / info ---
        if (path.equals("/info"))       { writeText(out, 200, infoJson(), "application/json"); return; }
        if (path.equals("/flags"))      { writeFlags(out); return; }
        // --- kamera ---
        if (path.equals("/snapshot"))   { writeSnapshot(out); return; }
        if (path.equals("/set"))        { applySet(query); writeText(out, 200, "ok"); return; }
        // --- skaerm (MediaProjection) ---
        if (path.equals("/screen.jpg")) { writeScreenSnapshot(out); return; }
        if (path.equals("/screen.codec")) { writeText(out, 200, h264Codec()); return; }   // MSE-codec-streng
        if (path.equals("/control"))    { writeText(out, 200, controlHtml(query), "text/html; charset=utf-8"); return; }
        if (path.equals("/controlhw"))  { writeText(out, 200, controlHwHtml(query), "text/html; charset=utf-8"); return; }   // H.264/MSE-viewer
        // --- input (a11y-motor, proxy til 8127); d = display (default 0) ---
        if (path.equals("/tap"))        { writeText(out, 200, rpc("tap " + intp(query,"x",0) + " " + intp(query,"y",0) + " " + intp(query,"d",0) + " " + intp(query,"ms",60))); return; }
        if (path.equals("/swipe"))      { writeText(out, 200, rpc("swipe " + intp(query,"x1",0) + " " + intp(query,"y1",0) + " " + intp(query,"x2",0) + " " + intp(query,"y2",0) + " " + intp(query,"d",0) + " " + intp(query,"ms",200))); return; }
        if (path.equals("/key"))        { String k = param(query,"k"); writeText(out, 200, "enter".equals(k) ? rpc("enter") : rpc("global " + keyName(k))); return; }
        if (path.equals("/text"))       { writeText(out, 200, rpc("text " + dparam(query,"t"))); return; }   // tastatur-input -> fokuseret felt
        if (path.equals("/wake"))       { writeText(out, 200, rpc("wake")); return; }   // vaek display 0 (telefonskaermen)
        if (path.equals("/click"))      { writeText(out, 200, rpc("click " + intp(query,"d",0) + " " + dparam(query,"match"))); return; }
        // --- UI-inspektion (a11y) ---
        if (path.equals("/find"))       { writeText(out, 200, rpc("find " + intp(query,"d",0) + " " + dparam(query,"match"))); return; }
        if (path.equals("/gettext"))    { writeText(out, 200, rpc("gettext " + intp(query,"d",0) + " " + dparam(query,"match"))); return; }
        if (path.equals("/exists"))     { writeText(out, 200, rpc("state " + intp(query,"d",0) + " " + dparam(query,"match"))); return; }
        if (path.equals("/dump"))       { writeText(out, 200, rpc("dump " + intp(query,"d",0))); return; }
        if (path.equals("/displays"))   { writeText(out, 200, rpc("displays")); return; }
        if (path.equals("/scroll"))     { writeText(out, 200, rpc("scroll " + intp(query,"d",0) + ("back".equals(param(query,"dir")) ? " b" : ""))); return; }
        // --- navigation / launch ---
        if (path.equals("/launch"))     { writeText(out, 200, rpc("launch " + intp(query,"d",0) + " " + dparam(query,"action") + opt(dparam(query,"data")) + opt(dparam(query,"pkg")))); return; }
        // --- management ---
        if (path.equals("/wd"))         { writeWd(out); return; }
        if (path.equals("/pair"))       { writePair(out); return; }
        if (path.equals("/devoptions")) { writeText(out, 200, rpc("devoptions" + ("1".equals(param(query,"probe")) ? " probe" : ""))); return; }
        if (path.equals("/update"))     { writeText(out, 200, triggerUpdate(query)); return; }
        // --- generisk a11y-passthrough (alle 8127-kommandoer; cmd URL-encodet) ---
        if (path.equals("/rpc"))        { writeText(out, 200, rpc(dparam(query,"cmd"))); return; }
        // --- hardware (sensorer + fysisk styring; Android 8+) ---
        if (path.equals("/sensors"))      { writeAuto(out, Hardware.sensorsList(Rig.ctx())); return; }
        if (path.equals("/sensor"))       { writeAuto(out, Hardware.readSensor(Rig.ctx(), param(query, "type"))); return; }
        if (path.equals("/battery"))      { writeAuto(out, Hardware.batteryJson(Rig.ctx())); return; }
        if (path.equals("/torch"))        { writeAuto(out, Hardware.torch(Rig.ctx(), boolp(query, "on"))); return; }
        if (path.equals("/vibrate"))      { writeAuto(out, Hardware.vibrate(Rig.ctx(), intp(query, "ms", 300))); return; }
        if (path.equals("/volume"))       { writeAuto(out, Hardware.volume(Rig.ctx(), param(query, "stream"), intOrNull(query, "level"))); return; }
        if (path.equals("/ringer"))       { writeAuto(out, Hardware.ringer(Rig.ctx(), param(query, "mode"))); return; }
        if (path.equals("/brightness"))   { writeAuto(out, Hardware.brightness(Rig.ctx(), intOrNull(query, "level"))); return; }
        if (path.equals("/display"))      { writeAuto(out, Hardware.displayJson(Rig.ctx())); return; }
        if (path.equals("/connectivity")) { writeAuto(out, Hardware.connectivityJson(Rig.ctx())); return; }
        if (path.equals("/location"))     { writeAuto(out, Hardware.locationJson(Rig.ctx())); return; }
        if (path.equals("/mic"))          { writeAuto(out, Hardware.micLevel(Rig.ctx())); return; }
        // --- bevaegelses-alarm (motion-detection + ntfy-push) ---
        if (path.equals("/motion"))       { writeText(out, 200, configMotion(query), "application/json"); return; }
        if (path.equals("/events"))       { writeText(out, 200, motionEventsJson(), "application/json"); return; }

        writeText(out, 404, "not found");
    }

    // /motion?on=1&topic=...&server=...&sensitivity=1..10 -> konfigurér bevaegelses-alarm (persisteres). Uden
    // parametre returneres blot den nuvaerende config. ntfy-topic tom = detektér + log (/events), men ingen push.
    private String configMotion(String query) {
        if (param(query, "on") != null)          Rig.motionEnabled = "1".equals(param(query, "on")) || "true".equals(param(query, "on"));
        if (dparam(query, "topic") != null)      Rig.ntfyTopic = dparam(query, "topic");
        if (dparam(query, "server") != null)     { String s = dparam(query, "server"); if (s != null && !s.isEmpty()) Rig.ntfyServer = s; }
        if (param(query, "sensitivity") != null) Rig.motionSensitivity = Math.max(1, Math.min(10, intp(query, "sensitivity", Rig.motionSensitivity)));
        try { Rig.saveMotionPrefs(Rig.ctx()); } catch (Throwable ignored) {}
        StringBuilder b = new StringBuilder();
        b.append("{\"enabled\":").append(Rig.motionEnabled)
         .append(",\"ntfyServer\":\"").append(jsonEsc(Rig.ntfyServer)).append("\"")
         .append(",\"ntfyTopic\":\"").append(jsonEsc(Rig.ntfyTopic)).append("\"")
         .append(",\"sensitivity\":").append(Rig.motionSensitivity)
         .append(",\"lastNtfy\":\"").append(jsonEsc(Rig.lastNtfy)).append("\"}");
        return b.toString();
    }

    private String motionEventsJson() {
        StringBuilder b = new StringBuilder("[");
        synchronized (Rig.motionEvents) {
            for (int i = 0; i < Rig.motionEvents.size(); i++) {
                if (i > 0) b.append(",");
                b.append(Rig.motionEvents.get(i));
            }
        }
        return b.append("]").toString();
    }

    private boolean tokenOk(String query) {
        String want = Rig.token;
        if (want == null || want.isEmpty()) return true;   // ingen token sat -> kun kilde-IP-ACL beskytter
        String got = param(query, "token");
        if (got == null) return false;
        try { return java.security.MessageDigest.isEqual(want.getBytes("UTF-8"), got.getBytes("UTF-8")); }  // konstant-tid
        catch (Throwable t) { return want.equals(got); }
    }

    private void writeSnapshot(OutputStream out) throws IOException {
        byte[] jpeg = Rig.latestJpeg;
        if (jpeg == null) { writeText(out, 503, "no frame yet"); return; }
        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.1 200 OK\r\n")
           .append("Content-Type: image/jpeg\r\n")
           .append("Content-Length: ").append(jpeg.length).append("\r\n")
           .append("Cache-Control: no-store\r\n")
           .append("Connection: keep-alive\r\n\r\n");
        out.write(hdr.toString().getBytes("UTF-8"));
        out.write(jpeg);
        out.flush();
    }

    private void writeStream(Socket c, OutputStream out) throws IOException {
        c.setSoTimeout(0);
        String boundary = "rigframe";
        String head = "HTTP/1.0 200 OK\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.flush();
        long sent = -1;
        long frameMs = 1000L / Math.max(1, Rig.targetFps);
        while (running) {
            byte[] jpeg = Rig.latestJpeg;
            long seq = Rig.latestSeq;
            if (jpeg != null && seq != sent) {
                sent = seq;
                StringBuilder p = new StringBuilder();
                p.append("--").append(boundary).append("\r\n")
                 .append("Content-Type: image/jpeg\r\n")
                 .append("Content-Length: ").append(jpeg.length).append("\r\n\r\n");
                out.write(p.toString().getBytes("UTF-8"));
                out.write(jpeg);
                out.write("\r\n".getBytes("UTF-8"));
                out.flush();
            }
            try { Thread.sleep(frameMs); } catch (InterruptedException e) { break; }
        }
    }

    private void writeWd(OutputStream out) throws IOException {
        RigAccessibilityService svc = Rig.a11y;
        if (svc == null) { writeText(out, 503, "{\"error\":\"a11y not enabled\"}", "application/json"); return; }
        String ipp = svc.recoverWirelessDebugging();
        if (ipp == null) { writeText(out, 500, "{\"error\":\"wd recovery failed\"}", "application/json"); return; }
        String ip = ipp.contains(":") ? ipp.substring(0, ipp.indexOf(':')) : ipp;
        String port = ipp.contains(":") ? ipp.substring(ipp.indexOf(':') + 1) : "";
        String json = "{\"ip\":\"" + ip + "\",\"port\":" + (port.isEmpty() ? "null" : port) + ",\"ipport\":\"" + ipp + "\"}";
        writeText(out, 200, json, "application/json");
    }

    // /pair: start WD-parring (a11y aabner dialogen) og returnér parrings-adresse + kode, saa
    // companion-installen kan parre en frisk PC hovedloest (rig'en er skaermloes). Engangs pr. PC.
    private void writePair(OutputStream out) throws IOException {
        RigAccessibilityService svc = Rig.a11y;
        if (svc == null) { writeText(out, 503, "{\"error\":\"a11y not enabled\"}", "application/json"); return; }
        String r = svc.startWdPairing();   // "ip:port kode"
        if (r == null) { writeText(out, 500, "{\"error\":\"pairing failed\"}", "application/json"); return; }
        int sp = r.lastIndexOf(' ');
        String addr = r.substring(0, sp), code = r.substring(sp + 1);
        writeText(out, 200, "{\"addr\":\"" + addr + "\",\"code\":\"" + code + "\"}", "application/json");
    }

    // /flags: skrivebeskyttet app-tilstand, saa en evt. overbygning kan laese fx DeX-reconnect-toggle.
    private void writeFlags(OutputStream out) throws IOException {
        boolean battOpt = false;   // ignorerer enheden batteri-optimering for Husk? (vigtigt for unattended drift)
        try {
            android.content.Context c = Rig.ctx();
            if (c != null) {
                android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(android.content.Context.POWER_SERVICE);
                battOpt = pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
            }
        } catch (Throwable ignored) {}
        String json = "{\"dexReconnect\":" + Rig.dexReconnect
                    + ",\"a11y\":" + (Rig.a11y != null)
                    + ",\"camera\":" + Rig.cameraRunning
                    + ",\"screen\":" + Rig.screenRunning
                    + ",\"motion\":" + Rig.motionEnabled
                    + ",\"ntfy\":" + (Rig.ntfyTopic != null && !Rig.ntfyTopic.isEmpty())
                    + ",\"batteryOptIgnored\":" + battOpt
                    + ",\"lastUpdate\":\"" + jsonEsc(Rig.lastUpdate) + "\""
                    + ",\"lastNtfy\":\"" + jsonEsc(Rig.lastNtfy) + "\"}";
        writeText(out, 200, json, "application/json");
    }

    // Trigger den indbyggede opdatering remote (over Tailscale) - resultatet/fejlen laeses i /flags
    // (lastUpdate). Bruger a11y-servicen som Context (den er en Service = Context). Token-gated som alt.
    // Fjern-self-update: bring appen i FORGRUNDEN (ellers blokerer Android install-dialogen = background-activity-
    // start), start opdateringen, og lad a11y auto-tappe samtykket. ?force=1 geninstallerer samme version (test).
    // Fejler sikkert: misser a11y-tappet, sker der INGEN install (PackageInstaller er atomisk -> nuvaerende bevares).
    private String triggerUpdate(String query) {
        final RigAccessibilityService svc = Rig.a11y;
        if (svc == null) return "ERR a11y ikke oppe (kan ikke forgrunde + samtykke)";
        final boolean force = boolp(query, "force");
        svc.foregroundSelf();
        new Thread(new Runnable() { public void run() { svc.acceptInstallConsent(); } }, "husk-accept").start();
        new Thread(new Runnable() { public void run() {
            try { Thread.sleep(1500); } catch (InterruptedException e) {}   // lad forgrunden lande foer dialogen
            Updater.checkAndUpdate(svc, force);
        } }, "husk-upd").start();
        return "remote self-update startet (foreground + auto-accept" + (force ? ", force" : "") + ") - laes /flags";
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch == '\n' || ch == '\r' || ch == '\t') b.append(' ');
            else b.append(ch);
        }
        return b.toString();
    }

    // /info: ét kald der giver alt om host-enheden (app, OS, skaerm, net, batteri, services). Bruger
    // a11y-servicen som Context; Context-afhaengige felter udelades (tomme/-1) hvis a11y ikke er oppe.
    private String infoJson() {
        String vn = "", vc = "";
        boolean dexCap = false, hasCam = false;
        int sw = Rig.screenW, sh = Rig.screenH, batt = -1;
        boolean charging = false;
        android.content.Context c = Rig.a11y;
        if (c != null) {
            try {
                android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                vn = pi.versionName;
                vc = String.valueOf(Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
            } catch (Throwable t) {}
            try { dexCap = DeXDetector.isDeXCapable(c); } catch (Throwable t) {}
            try { hasCam = c.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY); } catch (Throwable t) {}
            try { android.util.DisplayMetrics m = c.getResources().getDisplayMetrics(); if (sw == 0) { sw = m.widthPixels; sh = m.heightPixels; } } catch (Throwable t) {}
            try {
                android.os.BatteryManager bm = (android.os.BatteryManager) c.getSystemService(android.content.Context.BATTERY_SERVICE);
                batt = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (Build.VERSION.SDK_INT >= 23) charging = bm.isCharging();
            } catch (Throwable t) {}
        }
        String lan = Net.localIp(), ts = Net.tailscaleIp();
        StringBuilder b = new StringBuilder();
        b.append("{\"app\":{\"package\":\"co.xplat.husk\",\"versionName\":\"").append(jsonEsc(vn)).append("\",\"versionCode\":\"").append(vc).append("\"},");
        b.append("\"device\":{\"manufacturer\":\"").append(jsonEsc(Build.MANUFACTURER)).append("\",\"model\":\"").append(jsonEsc(Build.MODEL))
         .append("\",\"androidRelease\":\"").append(jsonEsc(Build.VERSION.RELEASE)).append("\",\"sdkInt\":").append(Build.VERSION.SDK_INT)
         .append(",\"dexCapable\":").append(dexCap).append(",\"hasCamera\":").append(hasCam).append("},");
        b.append("\"screen\":{\"width\":").append(sw).append(",\"height\":").append(sh).append("},");
        b.append("\"net\":{\"localIp\":").append(lan == null ? "null" : ("\"" + lan + "\"")).append(",\"tailscaleIp\":").append(ts == null ? "null" : ("\"" + ts + "\"")).append("},");
        b.append("\"battery\":{\"level\":").append(batt).append(",\"charging\":").append(charging).append("},");
        b.append("\"services\":{\"a11y\":").append(Rig.a11y != null).append(",\"camera\":").append(Rig.cameraRunning).append(",\"screen\":").append(Rig.screenRunning).append(",\"dexReconnect\":").append(Rig.dexReconnect).append("},");
        b.append("\"lastUpdate\":\"").append(jsonEsc(Rig.lastUpdate)).append("\"}");
        return b.toString();
    }

    // URL-decoded query-param (til regex/tekst/URL-vaerdier i /click //find //rpc //launch osv.).
    private static String dparam(String query, String key) {
        String v = param(query, key);
        if (v == null) return "";
        try { return java.net.URLDecoder.decode(v, "UTF-8"); } catch (Throwable t) { return v; }
    }

    private static String opt(String s) { return (s == null || s.isEmpty()) ? "" : (" " + s); }

    // Skriv med auto-valgt content-type: JSON hvis body ligner JSON ({ eller [), ellers text/plain.
    private void writeAuto(OutputStream out, String body) throws IOException {
        String ct = (body.startsWith("{") || body.startsWith("[")) ? "application/json; charset=utf-8" : "text/plain; charset=utf-8";
        writeText(out, 200, body, ct);
    }

    private static boolean boolp(String query, String key) {
        String v = param(query, key);
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }

    private static Integer intOrNull(String query, String key) {
        String v = param(query, key);
        if (v == null) return null;
        try { return Integer.valueOf(v); } catch (Throwable t) { return null; }
    }

    private void applySet(String query) {
        String rot = param(query, "rot");
        String flip = param(query, "flip");
        String fps = param(query, "fps");
        try { if (rot != null) Rig.rotation = Integer.parseInt(rot); } catch (Throwable ignored) {}
        if (flip != null) Rig.flip = flip.equals("1") || flip.equalsIgnoreCase("true");
        try { if (fps != null) Rig.targetFps = Math.max(1, Integer.parseInt(fps)); } catch (Throwable ignored) {}
        // Skaerm-stream tuning i farten (mod lag/baandbredde): sq = JPEG-kvalitet 1..100, sfps = skaerm-fps 1..30.
        try { String sq = param(query, "sq"); if (sq != null) Rig.screenQuality = Math.max(1, Math.min(100, Integer.parseInt(sq))); } catch (Throwable ignored) {}
        try { String sfps = param(query, "sfps"); if (sfps != null) { int f = Math.max(1, Math.min(30, Integer.parseInt(sfps))); Rig.screenMinFrameMs = Math.max(20, 1000 / f); } } catch (Throwable ignored) {}
        // Bemaerk: rotation slaar igennem ved naeste session-rebuild; her sat for snapshot/flip-vej.
    }

    private String viewerHtml() {
        // Minimal overvaagnings-visning (M2). token tilfoejes af brugeren i URL'en (?token=..)
        // -> img-stien arver query'en. Holdt bevidst trivielt.
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
             + "<title>Husk</title><body style='margin:0;background:#111'>"
             + "<img id=v style='width:100%;height:auto' src='/stream'>"
             + "<script>var t=location.search;if(t){document.getElementById('v').src='/stream'+t;}</script>";
    }

    // ---------------- skaerm (MediaProjection) + input-proxy til 8127-motoren ----------------

    private void writeScreenSnapshot(OutputStream out) throws IOException {
        // Signalér efterspoergsel (doven ScreenService genoptager produktion) + vent kort paa en FRISK frame:
        // efter idle er Rig.latestScreenJpeg evt. gammel/null indtil onFrame har lavet en ny.
        Rig.lastScreenClientMs = android.os.SystemClock.uptimeMillis();
        long seq0 = Rig.latestScreenSeq;
        byte[] jpeg = Rig.latestScreenJpeg;
        for (int i = 0; i < 50 && (jpeg == null || Rig.latestScreenSeq == seq0); i++) {
            try { Thread.sleep(15); } catch (InterruptedException e) { break; }
            jpeg = Rig.latestScreenJpeg;
        }
        if (jpeg == null) { writeText(out, 503, "ingen skærm-frame (slå skærmdeling til i appen)"); return; }
        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.1 200 OK\r\n")
           .append("Content-Type: image/jpeg\r\n")
           .append("Content-Length: ").append(jpeg.length).append("\r\n")
           .append("Cache-Control: no-store\r\n")
           .append("Connection: keep-alive\r\n\r\n");
        out.write(hdr.toString().getBytes("UTF-8"));
        out.write(jpeg);
        out.flush();
    }

    private void writeScreenStream(Socket c, OutputStream out) throws IOException {
        c.setSoTimeout(0);
        try { c.setTcpNoDelay(true); } catch (Throwable ignored) {}   // hver frame flushes straks (ingen Nagle)
        String boundary = "rigscreen";
        String head = "HTTP/1.0 200 OK\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.flush();
        long sent = -1, lastSent = 0;
        while (running) {
            Rig.lastScreenClientMs = android.os.SystemClock.uptimeMillis();   // efterspoergsel: hold ScreenService i gang mens klienten ser med
            byte[] jpeg = Rig.latestScreenJpeg;
            long seq = Rig.latestScreenSeq;
            long now = System.currentTimeMillis();
            // Send nye frames straks; GEN-send seneste frame hvert ~1s (keepalive) selv ved stilstand. Ellers
            // gengiver browserens <img> ikke multipart-stroemmen foer en skaerm-aendring -> /control stod tom
            // indtil man trykkede en knap. Keepalive viser ALTID den nuvaerende skaerm med det samme.
            if (jpeg != null && (seq != sent || now - lastSent > 1000)) {
                sent = seq; lastSent = now;
                StringBuilder p = new StringBuilder();
                p.append("--").append(boundary).append("\r\n")
                 .append("Content-Type: image/jpeg\r\n")
                 .append("Content-Length: ").append(jpeg.length).append("\r\n\r\n");
                out.write(p.toString().getBytes("UTF-8"));
                out.write(jpeg);
                out.write("\r\n".getBytes("UTF-8"));
                out.flush();
            }
            try { Thread.sleep(20); } catch (InterruptedException e) { break; }
        }
    }

    // ---------------- hardware-H.264 (MediaCodec -> fMP4 -> MSE) ----------------

    // /screen.mp4: live fMP4-stream. Starter H.264 lazily (deler ScreenService' projection), tilmelder klienten,
    // og holder forbindelsen aaben mens H264Stream skriver fragmenter til den. Lukker + river ned naar tom.
    private void writeH264Stream(Socket c, OutputStream out) throws IOException {
        c.setSoTimeout(0);
        try { c.setTcpNoDelay(true); } catch (Throwable ignored) {}
        H264Stream st = Rig.ensureH264();
        if (st == null) { writeText(out, 503, "ingen skærm/H.264 (slå skærmdeling til i appen)"); return; }
        String head = "HTTP/1.0 200 OK\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Type: video/mp4\r\n\r\n";
        out.write(head.getBytes("UTF-8")); out.flush();
        H264Stream.Client cl = st.addClient(out);
        try { while (running && st.hasClient(cl)) { Thread.sleep(400); } }
        catch (InterruptedException ignored) {}
        st.removeClient(cl);
        if (st.clientCount() == 0) Rig.stopH264();   // lazy teardown: frigiv encoder + 2.-VirtualDisplay
    }

    // /screen.codec: starter H.264 (lazy) + venter til SPS er kendt -> "avc1.PPCCLL" til MSE addSourceBuffer.
    private String h264Codec() {
        H264Stream st = Rig.ensureH264();
        if (st == null) return "";
        for (int i = 0; i < 30; i++) {
            String cs = st.getCodec();
            if (cs != null && !cs.isEmpty()) return cs;
            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
        }
        return "";
    }

    // /controlhw: hardware-accelereret (H.264/MSE) udgave af /control - lavere lag + baandbredde. Hele skaermen
    // passer i vinduet; klik=tap, traek=swipe (samme input-mapping som /control). /control (MJPEG) er fallback.
    private String controlHwHtml(String query) {
        String tok = param(query, "token");
        String amp = (tok == null || tok.isEmpty()) ? "" : "&token=" + tok;
        String tq = (tok == null || tok.isEmpty()) ? "" : "?token=" + tok;
        int w = Rig.screenW, h = Rig.screenH;
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
             + "<title>Husk control (HW)</title>"
             + "<body style='margin:0;height:100vh;height:100dvh;display:flex;flex-direction:column;background:#111;color:#ccc;font-family:sans-serif;text-align:center;overflow:hidden;touch-action:manipulation'>"
             + "<div style='flex:none;padding:6px'>"
             + "<button onclick=\"k('back')\">Tilbage</button> "
             + "<button onclick=\"k('home')\">Hjem</button> "
             + "<button onclick=\"k('recents')\">Recents</button>"
             + " <span id=stx style='font-size:12px;color:#888'>H.264 starter &middot; klik=tap &middot; træk=swipe</span>"
             + " <a href='/control" + tq + "' style='color:#6cf;font-size:12px'>MJPEG-fallback</a>"
             + " <input id=kb placeholder='tastatur (skriv i fokuseret felt)' autocomplete=off autocapitalize=none autocorrect=off spellcheck=false style='font-size:14px;min-width:140px'></div>"
             + "<div style='flex:1;min-height:0;display:flex;align-items:center;justify-content:center;overflow:hidden'>"
             + "<video id=v muted autoplay playsinline style='max-width:100%;max-height:100%;touch-action:none;background:#000'></video></div>"
             + "<script>var W=" + w + ",H=" + h + ",A='" + amp + "',Q='" + tq + "';"
             + "var v=document.getElementById('v'),stx=document.getElementById('stx');"
             + "function k(n){fetch('/key?k='+n+A);}"
             + "function say(m){stx.textContent=m;}"
             + "var sx=0,sy=0,t0=0,dn=false;"
             + "function rp(e){var r=v.getBoundingClientRect();return{cx:e.clientX-r.left,cy:e.clientY-r.top,rw:r.width,rh:r.height};}"
             + "v.addEventListener('pointerdown',function(e){if(!W||!H)return;var p=rp(e);sx=p.cx;sy=p.cy;t0=Date.now();dn=true;e.preventDefault();});"
             + "v.addEventListener('pointerup',function(e){if(!dn||!W||!H)return;dn=false;var p=rp(e);"
             + "var X1=Math.round(sx/p.rw*W),Y1=Math.round(sy/p.rh*H),X2=Math.round(p.cx/p.rw*W),Y2=Math.round(p.cy/p.rh*H);"
             + "if(Math.abs(p.cx-sx)+Math.abs(p.cy-sy)<10){fetch('/tap?x='+X1+'&y='+Y1+A);}"
             + "else{fetch('/swipe?x1='+X1+'&y1='+Y1+'&x2='+X2+'&y2='+Y2+'&ms='+Math.min(800,Math.max(60,Date.now()-t0))+A);}});"
             + "var kb=document.getElementById('kb');"
             + "kb.addEventListener('input',function(){fetch('/text?t='+encodeURIComponent(kb.value)+A);});"
             + "kb.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();fetch('/key?k=enter'+A);}});"
             + "function sleep(ms){return new Promise(function(r){setTimeout(r,ms);});}"
             + "async function go(){"
             + "var codec='';for(var i=0;i<25&&!codec;i++){try{var t=(await (await fetch('/screen.codec'+Q)).text()).trim();if(t.indexOf('avc1')==0)codec=t;}catch(e){}if(!codec)await sleep(200);}"
             + "if(!codec){say('H.264 ikke klar - brug /control');return;}"
             + "if(!('MediaSource' in window)){say('MSE mangler - brug /control');return;}"
             + "var mime='video/mp4; codecs=\"'+codec+'\"';"
             + "if(!MediaSource.isTypeSupported(mime)){say('codec ikke støttet: '+codec);return;}"
             + "var ms=new MediaSource();v.src=URL.createObjectURL(ms);"
             + "ms.addEventListener('sourceopen',function(){"
             + "if(ms.sourceBuffers.length){return;}"   // sourceopen kan fyre igen -> undgaa dobbelt addSourceBuffer
             + "var sb;try{sb=ms.addSourceBuffer(mime);}catch(e){say('addSourceBuffer: '+e);return;}"
             + "try{sb.mode='sequence';}catch(e){}"
             + "var q=[];function flush(){if(sb.updating||!q.length)return;try{sb.appendBuffer(q.shift());}catch(e){}}"
             + "sb.addEventListener('updateend',function(){flush();});"
             // LIVE-EDGE-TRACKER (lav lag UDEN at toemme bufferen): <video> driver bagud fra live-kanten. Spol GLAT op
             // via en lille playbackRate naar driften er stor; aldrig saa aggressivt at bufferen toemmes (= stall/frys).
             // For-aggressivt (1.5x ned til 0.25s) gav underrun; her holdes ~0.4s sikkert. MSE har et reelt buffer-gulv
             // (modsat MJPEG, der bytter raa frames straks) - derfor er MJPEG stadig lavere lag paa en hurtig forbindelse.
             + "setInterval(function(){try{if(!v.buffered.length||v.paused)return;var end=v.buffered.end(v.buffered.length-1),gap=end-v.currentTime;if(gap>2.5){v.currentTime=end-0.4;v.playbackRate=1.0;}else if(gap>0.5){v.playbackRate=1.3;}else{v.playbackRate=1.0;}}catch(e){}},200);"
             + "fetch('/screen.mp4'+Q).then(function(resp){var rd=resp.body.getReader();say('H.264 (HW) live');(function pump(){rd.read().then(function(r){if(r.done){return;}q.push(r.value);flush();pump();}).catch(function(){});})();}).catch(function(e){say('stream-fejl: '+e);});"
             + "});"
             + "v.play().catch(function(){});"
             + "}go();"
             + "</script>";
    }

    // Proxy en kommando til a11y-motoren paa 127.0.0.1:8127 (samme linjeprotokol som engine.rpc),
    // saa browser-styring (/tap //swipe //key) injiceres via a11y - ingen adb/WD noedvendig.
    private String rpc(String cmd) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress("127.0.0.1", 8127), 3000);
            s.setSoTimeout(8000);
            s.getOutputStream().write((cmd + "\n").getBytes("UTF-8"));
            s.getOutputStream().flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = br.readLine();
            return line == null ? "" : line;
        } catch (Throwable t) {
            return "ERR a11y (8127) ikke oppe";
        } finally {
            if (s != null) { try { s.close(); } catch (Throwable ignored) {} }
        }
    }

    private static String keyName(String k) {
        if (k != null && (k.equals("home") || k.equals("recents") || k.equals("back") || k.equals("notifications"))) return k;
        return "back";
    }

    private static int intp(String query, String key, int def) {
        try { String v = param(query, key); return v == null ? def : Integer.parseInt(v); } catch (Throwable t) { return def; }
    }

    // /control: skaerm-stream + klik->a11y-tap (mapper klik-px til de RIGTIGE skaerm-px via screenW/H)
    // + Tilbage/Hjem/Recents. token arves fra ?token= saa img + fetch er autoriseret.
    private String controlHtml(String query) {
        String tok = param(query, "token");
        String amp = (tok == null || tok.isEmpty()) ? "" : "&token=" + tok;
        String tq = (tok == null || tok.isEmpty()) ? "" : "?token=" + tok;
        int w = Rig.screenW, h = Rig.screenH;
        return "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
             + "<title>Husk control</title>"
             // Flex-kolonne i fuld vindueshoejde: knapper foroven, billedet fylder resten og SKALERES SAA HELE
             // den streamede skaerm passer i vinduet (max-height:100%) - ellers (touch-action:none) kunne man
             // ikke scrolle siden paa en telefon og saa kun nederste/oeverste del af skaermen var synlig.
             + "<body style='margin:0;height:100vh;height:100dvh;display:flex;flex-direction:column;background:#111;color:#ccc;font-family:sans-serif;text-align:center;overflow:hidden;touch-action:manipulation'>"
             + "<div style='flex:none;padding:6px'>"
             + "<button onclick=\"k('back')\">Tilbage</button> "
             + "<button onclick=\"k('home')\">Hjem</button> "
             + "<button onclick=\"k('recents')\">Recents</button>"
             + " <span style='font-size:12px;color:#888'>klik=tap &middot; træk=swipe/scroll</span>"
             + " <a href='/controlhw" + tq + "' style='color:#6cf;font-size:12px'>HW (H.264, mindre lag)</a>"
             + " <input id=kb placeholder='tastatur (skriv i fokuseret felt)' autocomplete=off autocapitalize=none autocorrect=off spellcheck=false style='font-size:14px;min-width:140px'></div>"
             + "<div style='flex:1;min-height:0;display:flex;align-items:center;justify-content:center;overflow:hidden'>"
             + "<img id=v style='max-width:100%;max-height:100%;touch-action:none' src='/screen" + tq + "'></div>"
             + "<script>var W=" + w + ",H=" + h + ",A='" + amp + "';"
             + "var img=document.getElementById('v'),sx=0,sy=0,st=0,dn=false;"
             + "function k(n){fetch('/key?k='+n+A);}"
             + "function rp(e){var r=img.getBoundingClientRect();return{cx:e.clientX-r.left,cy:e.clientY-r.top,rw:r.width,rh:r.height};}"
             // træk = swipe (scroll/skift skærm), kort klik = tap. Pointer-events dækker både mus + touch.
             + "img.addEventListener('pointerdown',function(e){if(!W||!H)return;var p=rp(e);sx=p.cx;sy=p.cy;st=Date.now();dn=true;e.preventDefault();});"
             + "img.addEventListener('pointerup',function(e){if(!dn||!W||!H)return;dn=false;var p=rp(e);"
             + "var X1=Math.round(sx/p.rw*W),Y1=Math.round(sy/p.rh*H),X2=Math.round(p.cx/p.rw*W),Y2=Math.round(p.cy/p.rh*H);"
             + "if(Math.abs(p.cx-sx)+Math.abs(p.cy-sy)<10){fetch('/tap?x='+X1+'&y='+Y1+A);}"
             + "else{fetch('/swipe?x1='+X1+'&y1='+Y1+'&x2='+X2+'&y2='+Y2+'&ms='+Math.min(800,Math.max(60,Date.now()-st))+A);}});"
             // tastatur: send hele feltets indhold ved hver taste-aendring (spejler det fokuserede felt) + Enter
             + "var kb=document.getElementById('kb');"
             + "kb.addEventListener('input',function(){fetch('/text?t='+encodeURIComponent(kb.value)+A);});"
             + "kb.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();fetch('/key?k=enter'+A);}});"
             + "</script>";
    }

    // ---------------- helpers ----------------

    private static String param(String query, String key) {
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int e = kv.indexOf('=');
            String k = e < 0 ? kv : kv.substring(0, e);
            if (k.equals(key)) return e < 0 ? "" : kv.substring(e + 1);
        }
        return null;
    }

    private void writeText(OutputStream out, int code, String body) throws IOException {
        writeText(out, code, body, "text/plain; charset=utf-8");
    }

    private void writeText(OutputStream out, int code, String body, String ctype) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.1 ").append(code).append(code == 200 ? " OK" : " ERR").append("\r\n")
           .append("Content-Type: ").append(ctype).append("\r\n")
           .append("Content-Length: ").append(b.length).append("\r\n")
           .append("Cache-Control: no-store\r\n")
           .append("Connection: keep-alive\r\n\r\n");   // genbrug forbindelsen -> ingen handshake-RTT pr. tap
        out.write(hdr.toString().getBytes("UTF-8"));
        out.write(b);
        out.flush();
    }

    public void stopServer() {
        running = false;
        if (adbForward != null) { try { adbForward.stop(); } catch (Throwable ignored) {} }
        for (ServerSocket ss : listeners) {
            try { ss.close(); } catch (Throwable ignored) {}
        }
        listeners.clear();
    }
}
