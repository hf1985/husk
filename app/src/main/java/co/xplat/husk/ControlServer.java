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

    private void handle(Socket c) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String reqLine = r.readLine();
        if (reqLine == null) return;
        // drain headers
        String h; while ((h = r.readLine()) != null && h.length() > 0) { /* ignore */ }

        String[] parts = reqLine.split(" ");
        if (parts.length < 2) { writeText(c.getOutputStream(), 400, "bad request"); return; }
        String path = parts[1];
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) { query = path.substring(q + 1); path = path.substring(0, q); }

        OutputStream out = c.getOutputStream();

        if (path.equals("/healthz")) { writeText(out, 200, "ok"); return; }
        if (path.equals("/")) { writeText(out, 200, viewerHtml(), "text/html; charset=utf-8"); return; }

        if (!tokenOk(query)) { writeText(out, 401, "unauthorized"); return; }

        // --- status / info ---
        if (path.equals("/info"))       { writeText(out, 200, infoJson(), "application/json"); return; }
        if (path.equals("/flags"))      { writeFlags(out); return; }
        // --- kamera ---
        if (path.equals("/snapshot"))   { writeSnapshot(out); return; }
        if (path.equals("/stream"))     { writeStream(c, out); return; }
        if (path.equals("/set"))        { applySet(query); writeText(out, 200, "ok"); return; }
        // --- skaerm (MediaProjection) ---
        if (path.equals("/screen"))     { writeScreenStream(c, out); return; }
        if (path.equals("/screen.jpg")) { writeScreenSnapshot(out); return; }
        if (path.equals("/control"))    { writeText(out, 200, controlHtml(query), "text/html; charset=utf-8"); return; }
        // --- input (a11y-motor, proxy til 8127); d = display (default 0) ---
        if (path.equals("/tap"))        { writeText(out, 200, rpc("tap " + intp(query,"x",0) + " " + intp(query,"y",0) + " " + intp(query,"d",0) + " " + intp(query,"ms",60))); return; }
        if (path.equals("/swipe"))      { writeText(out, 200, rpc("swipe " + intp(query,"x1",0) + " " + intp(query,"y1",0) + " " + intp(query,"x2",0) + " " + intp(query,"y2",0) + " " + intp(query,"d",0) + " " + intp(query,"ms",200))); return; }
        if (path.equals("/key"))        { writeText(out, 200, rpc("global " + keyName(param(query,"k")))); return; }
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
        if (path.equals("/update"))     { writeText(out, 200, triggerUpdate()); return; }
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
        hdr.append("HTTP/1.0 200 OK\r\n")
           .append("Content-Type: image/jpeg\r\n")
           .append("Content-Length: ").append(jpeg.length).append("\r\n")
           .append("Cache-Control: no-store\r\n\r\n");
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
    private String triggerUpdate() {
        android.content.Context c = Rig.a11y;
        if (c == null) return "ERR a11y (context) ikke oppe";
        Updater.checkAndUpdate(c);
        return "update startet - laes /flags (lastUpdate)";
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
        byte[] jpeg = Rig.latestScreenJpeg;
        if (jpeg == null) { writeText(out, 503, "ingen skærm-frame (slå skærmdeling til i appen)"); return; }
        StringBuilder hdr = new StringBuilder();
        hdr.append("HTTP/1.0 200 OK\r\n")
           .append("Content-Type: image/jpeg\r\n")
           .append("Content-Length: ").append(jpeg.length).append("\r\n")
           .append("Cache-Control: no-store\r\n\r\n");
        out.write(hdr.toString().getBytes("UTF-8"));
        out.write(jpeg);
        out.flush();
    }

    private void writeScreenStream(Socket c, OutputStream out) throws IOException {
        c.setSoTimeout(0);
        String boundary = "rigscreen";
        String head = "HTTP/1.0 200 OK\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=" + boundary + "\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.flush();
        long sent = -1;
        while (running) {
            byte[] jpeg = Rig.latestScreenJpeg;
            long seq = Rig.latestScreenSeq;
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
            try { Thread.sleep(120); } catch (InterruptedException e) { break; }
        }
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
             + " <span style='font-size:12px;color:#888'>klik=tap &middot; træk=swipe/scroll</span></div>"
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
        hdr.append("HTTP/1.0 ").append(code).append(code == 200 ? " OK" : " ERR").append("\r\n")
           .append("Content-Type: ").append(ctype).append("\r\n")
           .append("Content-Length: ").append(b.length).append("\r\n")
           .append("Cache-Control: no-store\r\n")
           .append("Connection: close\r\n\r\n");
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
