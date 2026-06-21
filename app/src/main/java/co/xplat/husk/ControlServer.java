// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

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

    private final java.util.Set<String> boundHosts = new java.util.HashSet<String>();

    public void start() {
        running = true;
        bindHost("127.0.0.1");        // altid loopback med det samme
        startRebindLoop();            // bind LAN + Tailscale saa snart de dukker op (ogsaa efter boot)
        Log.i(TAG, "control-server lytter paa " + PORT + " (loopback; LAN + Tailscale bindes naar de er oppe)");
    }

    // Bind een host idempotent (undgaa dobbelt-bind af samme adresse).
    private synchronized void bindHost(String host) {
        if (boundHosts.contains(host)) return;
        bindAndServe(host);
        boundHosts.add(host);
    }

    // Tailscale-IP'en findes maaske ikke ved boot (Husk kan starte FOER Tailscale er oppe). Poll og
    // bind den naar den dukker op - saa slipper man for at toggle noget manuelt efter reboot. Starter
    // ogsaa adb-broen (scrcpy) naar Tailscale er der. Een gang pr. fundet IP.
    private void startRebindLoop() {
        Thread t = new Thread(new Runnable() { public void run() {
            while (running) {
                try {
                    for (String ip : Net.serveIps()) {     // LAN + Tailscale -> virker baade med og uden Tailscale
                        if (!boundHosts.contains(ip)) {
                            bindHost(ip);
                            if (Net.isTailscale(ip) && adbForward == null) { adbForward = new AdbForward(); adbForward.start(ip); }
                            Log.i(TAG, "control-server bandt " + ip);
                        }
                    }
                } catch (Throwable ignored) {}
                try { Thread.sleep(12000); } catch (InterruptedException e) { break; }
            }
        } }, "rig-http-rebind");
        t.setDaemon(true);
        t.start();
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

        if (path.equals("/snapshot")) { writeSnapshot(out); return; }
        if (path.equals("/stream"))   { writeStream(c, out); return; }
        if (path.equals("/wd"))       { writeWd(out); return; }
        if (path.equals("/pair"))     { writePair(out); return; }
        if (path.equals("/flags"))    { writeFlags(out); return; }
        if (path.equals("/set"))      { applySet(query); writeText(out, 200, "ok"); return; }
        if (path.equals("/screen"))     { writeScreenStream(c, out); return; }
        if (path.equals("/screen.jpg")) { writeScreenSnapshot(out); return; }
        if (path.equals("/control"))    { writeText(out, 200, controlHtml(query), "text/html; charset=utf-8"); return; }
        if (path.equals("/tap"))        { writeText(out, 200, rpc("tap " + intp(query,"x",0) + " " + intp(query,"y",0) + " 0")); return; }
        if (path.equals("/swipe"))      { writeText(out, 200, rpc("swipe " + intp(query,"x1",0) + " " + intp(query,"y1",0) + " " + intp(query,"x2",0) + " " + intp(query,"y2",0) + " 0 " + intp(query,"ms",200))); return; }
        if (path.equals("/key"))        { writeText(out, 200, rpc("global " + keyName(param(query,"k")))); return; }
        if (path.equals("/update"))     { writeText(out, 200, triggerUpdate()); return; }

        writeText(out, 404, "not found");
    }

    private boolean tokenOk(String query) {
        String want = Rig.token;
        if (want == null || want.isEmpty()) return true;   // ingen token sat -> kun ACL/bind beskytter
        return want.equals(param(query, "token"));
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
        String json = "{\"dexReconnect\":" + Rig.dexReconnect
                    + ",\"a11y\":" + (Rig.a11y != null)
                    + ",\"camera\":" + Rig.cameraRunning
                    + ",\"screen\":" + Rig.screenRunning
                    + ",\"lastUpdate\":\"" + jsonEsc(Rig.lastUpdate) + "\"}";
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
        if (jpeg == null) { writeText(out, 503, "no screen frame (start skaermdeling i appen)"); return; }
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
             + "<body style='margin:0;background:#111;color:#ccc;font-family:sans-serif;text-align:center;touch-action:manipulation'>"
             + "<div style='padding:6px'>"
             + "<button onclick=\"k('back')\">Tilbage</button> "
             + "<button onclick=\"k('home')\">Hjem</button> "
             + "<button onclick=\"k('recents')\">Recents</button></div>"
             + "<img id=v style='max-width:100%;height:auto;display:inline-block' src='/screen" + tq + "'>"
             + "<script>var W=" + w + ",H=" + h + ",A='" + amp + "';"
             + "var img=document.getElementById('v');"
             + "function k(n){fetch('/key?k='+n+A);}"
             + "img.addEventListener('click',function(e){var r=img.getBoundingClientRect();"
             + "if(!W||!H)return;"
             + "var x=Math.round((e.clientX-r.left)/r.width*W),y=Math.round((e.clientY-r.top)/r.height*H);"
             + "fetch('/tap?x='+x+'&y='+y+A);});"
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
