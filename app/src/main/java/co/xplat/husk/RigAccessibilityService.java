// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A11y-service for den samlede rig. Afledt af DexRPC's TapService. Den eksponerer SAMME
// loopback-RPC paa 127.0.0.1:8127 med BYTE-IDENTISK protokol som DexRPC -> appen er en
// drop-in DexRPC-superset, saa den slankede meeting-camera-overbygning (lib.sh::dex) koerer
// UAENDRET ovenpaa appen i stedet for dextap. Samme service driver desuden WD-recovery
// IN-PROCESS (port af wd-up.sh) via de samme node-operationer - een motor, een sti.
//
// SIKKERHED (samme accepterede risiko som DexRPC, se README "Sikkerhed"): 8127-socketten
// binder KUN 127.0.0.1, aldrig en netvaerks-adresse. Enhver lokal app med INET kan naa den og
// tappe/klikke/laese alle displays - accepteret paa en enkelt-formaals rig med kontrolleret
// app-saet, hvor fjernfladen daekkes af Tailscale-ACL. HTTP-fladen (/wd, /stream) er desuden
// token+Tailscale-gated. Bliver app-miljoeet mindre betroet: tilfoej en delt token i protokollen.
public class RigAccessibilityService extends AccessibilityService {
    static final String TAG = "Husk";
    static final int TCP_PORT = 8127;   // bind KUN 127.0.0.1 - se sikkerhedsnoten ovenfor

    private Handler mainHandler;
    private boolean serverStarted = false;
    private volatile boolean serverRunning = false;
    private ServerSocket tcpServer;

    interface Job { String run() throws Exception; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mainHandler = new Handler(Looper.getMainLooper());
        Rig.a11y = this;
        if (!serverStarted) { serverStarted = true; startTcpServer(); }
        Log.i(TAG, "a11y connected (8127-RPC + in-process WD-recovery klar)");
    }

    // ---------------- engine.rpc: loopback-RPC 127.0.0.1:8127 (DexRPC-superset, drop-in) ----------------
    //
    // Een kommando pr. forbindelse, linjebaseret; svar afsluttes med "\n" og luk. Protokollen er
    // identisk med DexRPC's TapService, saa meeting-camera-shellens dex()/dex_ok() virker uaendret.
    // Kommandoer: ping, displays, rotation, tap, swipe, find, click, state, gettext, dump, launch,
    // scroll, global. Node-ops broer til main via onMain (a11y-API kraever main-traad).

    private void startTcpServer() {
        serverRunning = true;
        Thread th = new Thread(new Runnable() {
            public void run() {
                // WATCHDOG: ydre loop re-binder + genstarter accept-loopet hvis det doer, saa 8127
                // ALDRIG forsvinder permanent ved en transient fejl (laering fra incident 2026-06-19:
                // en doed motor uden remote-vej er fatal). Stopper kun naar serverRunning=false (onDestroy).
                while (serverRunning) {
                    ServerSocket ss = null;
                    // Bind med SO_REUSEADDR + retry (TIME_WAIT-handoff fra en tidligere ejer af 8127).
                    for (int attempt = 0; attempt < 12 && ss == null && serverRunning; attempt++) {
                        try {
                            ss = new ServerSocket();
                            ss.setReuseAddress(true);
                            ss.bind(new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), TCP_PORT), 8);
                        } catch (Throwable t) {
                            ss = null;
                            if (attempt == 0) Log.w(TAG, "8127 optaget - retry", t);
                            try { Thread.sleep(5000); } catch (InterruptedException ie) { return; }
                        }
                    }
                    if (ss == null) {
                        Log.e(TAG, "kunne ikke binde 8127 - venter og proever igen (HTTP/WD virker imens)");
                        try { Thread.sleep(5000); } catch (InterruptedException ie) { return; }
                        continue;   // watchdog: bliv ved med at proeve
                    }
                    tcpServer = ss;
                    Log.i(TAG, "tcp socket up: 127.0.0.1:" + TCP_PORT);
                    try {
                        while (serverRunning) {
                            Socket c = ss.accept();
                            try { serve(c.getInputStream(), c.getOutputStream()); }
                            catch (Throwable t) { Log.e(TAG, "serve(tcp)", t); }
                            finally { try { c.close(); } catch (Throwable ig) { } }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "tcp accept-loop doede - watchdog re-binder", t);
                    } finally {
                        try { ss.close(); } catch (Throwable ig) { }
                    }
                    if (serverRunning) { try { Thread.sleep(2000); } catch (InterruptedException ie) { return; } }
                }
            }
        }, "rig-rpc-tcp");
        th.setDaemon(true);
        th.start();
    }

    private void serve(InputStream in, OutputStream out) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line = r.readLine();
        String resp = (line == null) ? "ERR empty" : handleRpc(line.trim());
        out.write((resp + "\n").getBytes("UTF-8"));
        out.flush();
    }

    private String handleRpc(String line) {
        if (line.length() == 0) return "ERR empty";
        final String[] tok = line.split("\\s+");
        final String cmd = tok[0];
        try {
            if (cmd.equals("ping")) return "PONG";
            if (cmd.equals("displays")) {
                return onMain(new Job() { public String run() { return listDisplays(); } }, 2000);
            }
            if (cmd.equals("rotation")) {
                final int d = Integer.parseInt(tok[1]);
                return onMain(new Job() { public String run() { return rotationOf(d); } }, 2000);
            }
            if (cmd.equals("tap")) {
                int x = Integer.parseInt(tok[1]), y = Integer.parseInt(tok[2]), d = Integer.parseInt(tok[3]);
                int dur = tok.length > 4 ? Integer.parseInt(tok[4]) : 60;
                return doGesture(x, y, -1, -1, d, dur);
            }
            if (cmd.equals("swipe")) {
                int x = Integer.parseInt(tok[1]), y = Integer.parseInt(tok[2]);
                int x2 = Integer.parseInt(tok[3]), y2 = Integer.parseInt(tok[4]), d = Integer.parseInt(tok[5]);
                int dur = tok.length > 6 ? Integer.parseInt(tok[6]) : 200;
                return doGesture(x, y, x2, y2, d, dur);
            }
            if (cmd.equals("dump")) {
                final int d = Integer.parseInt(tok[1]);
                return onMain(new Job() { public String run() { return dumpDisplay(d); } }, 6000);
            }
            if (cmd.equals("find") || cmd.equals("click") || cmd.equals("state") || cmd.equals("gettext")) {
                String rest = line.substring(cmd.length()).trim();   // "<d> <regex...>"
                int sp = rest.indexOf(' ');
                final int d = Integer.parseInt(sp < 0 ? rest : rest.substring(0, sp));
                final String pat = sp < 0 ? "" : rest.substring(sp + 1).trim();
                if (pat.length() == 0) return "ERR no-pattern";
                final Pattern pattern = Pattern.compile(pat, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                final String op = cmd;
                return onMain(new Job() {
                    public String run() {
                        AccessibilityNodeInfo n = findNode(d, pattern);
                        if (op.equals("state")) return n != null ? "1" : "0";
                        if (n == null) return "NONE";
                        if (op.equals("click")) return performClick(n);
                        if (op.equals("gettext")) {
                            CharSequence t = n.getText();
                            if (t != null && t.length() > 0) return t.toString();
                            CharSequence dd = n.getContentDescription();
                            return dd != null ? dd.toString() : "";
                        }
                        Rect r = new Rect();
                        n.getBoundsInScreen(r);
                        return ((r.left + r.right) / 2) + " " + ((r.top + r.bottom) / 2);
                    }
                }, 6000);
            }
            if (cmd.equals("launch")) {
                final int d = Integer.parseInt(tok[1]);
                final String action = tok[2];
                final String data = tok.length > 3 ? tok[3] : null;
                final String pkg = tok.length > 4 ? tok[4] : null;
                return onMain(new Job() { public String run() { return doLaunch(d, action, data, pkg); } }, 4000);
            }
            if (cmd.equals("scroll")) {
                final int d = Integer.parseInt(tok[1]);
                final boolean back = tok.length > 2 && tok[2].startsWith("b");
                return onMain(new Job() { public String run() { return doScrollRpc(d, back); } }, 4000);
            }
            if (cmd.equals("global")) {
                final String name = tok.length > 1 ? tok[1] : "";
                return onMain(new Job() { public String run() { return doGlobal(name); } }, 3000);
            }
            if (cmd.equals("devoptions")) {   // aktiver Udviklerindstillinger (7-tap) via a11y; 'probe' = kun navigation
                final boolean probe = tok.length > 1 && tok[1].equals("probe");
                return ensureDeveloperOptions(probe) ? (probe ? "OK found" : "OK enabled") : "ERR not-enabled";
            }
        } catch (Exception e) {
            return "ERR " + e;
        }
        return "ERR unknown:" + cmd;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (Rig.a11y == this) Rig.a11y = null;
        serverRunning = false;
        if (tcpServer != null) { try { tcpServer.close(); } catch (Exception e) { } }
        super.onDestroy();
    }

    // ---------------- main-thread bridge (node-ops skal koere paa main) ----------------

    private String onMain(final Job job, final long timeoutMs) {
        final String[] box = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(new Runnable() {
            public void run() {
                try { box[0] = job.run(); }
                catch (Throwable t) { box[0] = "ERR " + t; Log.e(TAG, "job", t); }
                finally { latch.countDown(); }
            }
        });
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return "ERR timeout";
        } catch (InterruptedException e) {
            return "ERR interrupted";
        }
        return box[0] == null ? "ERR null" : box[0];
    }

    // ---------------- offentlige op'er (samme semantik som DexRPC, brugt af WD-recovery) ----------------

    String stateD(final int d, final String regex) {   // "1" hvis node matcher, ellers "0"
        final Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return onMain(new Job() { public String run() { return findNode(d, p) != null ? "1" : "0"; } }, 6000);
    }

    String clickD(final int d, final String regex) {
        final Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return onMain(new Job() { public String run() {
            AccessibilityNodeInfo n = findNode(d, p);
            return n == null ? "NONE" : performClick(n);
        } }, 6000);
    }

    // Tap "Start nu"/"Start now" i MediaProjection-samtykke-dialogen, saa skaermdeling kan etableres
    // hands-free (permanent efter boot). Tikker "Vis ikke igen" een gang foerst hvis den findes (Android
    // 9 husker da samtykket). Retry mens dialogen dukker op. Koeres paa arbejder-traad (klik broer til main).
    String acceptScreenConsent() {
        boolean ticked = false;
        for (int i = 0; i < 14; i++) {
            if (!ticked) {
                String dnt = clickD(0, "don.?t show again|vis ikke igen");
                if (dnt != null && !dnt.equals("NONE")) ticked = true;
            }
            String r = clickD(0, "start now|start nu");
            if (r != null && !r.equals("NONE")) return r;   // knap fundet + klik forsoegt
            sleep(500);
        }
        return "NONE";
    }

    // Bring appens EGEN MainActivity i forgrunden. Noedvendigt foer en fjern-udloest opdatering: Android blokerer
    // baggrunds-apps fra at vise install-dialogen (background-activity-start), saa /update virker kun naar appen
    // er i forgrunden. En a11y-service MAA starte aktiviteter (samme vej som WD-recovery launcher Settings).
    // getLaunchIntentForPackage giver den rigtige MAIN+LAUNCHER-intent (kan IKKE laves via den generiske launch-RPC).
    String foregroundSelf() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (i == null) return "ERR no-launch-intent";
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            return "OK";
        } catch (Throwable t) { return "ERR " + t; }
    }

    // Auto-accepter systemets install-bekraeftelse (PackageInstaller) ved fjern-opdatering: tap hoved-knappen
    // (Update/Install/Opdater/Installer), evt. Play Protect ("install anyway"), og den afsluttende Open/Done.
    // Loeber ~24s, da dialogen kan komme forsinket (download) + i flere trin. clickD rammer kun KLIKBARE noder,
    // saa knappen (ikke broedtekst) tappes. Naar installen commit'er, draebes appen (inkl. denne traad) + relaunches.
    String acceptInstallConsent() {
        for (int i = 0; i < 24; i++) {
            clickD(0, "(?i)install anyway|installer alligevel|send anyway");   // Play Protect (hvis den kommer)
            clickD(0, "(?i)^\\s*(update|install|opdater|installer|geninstaller|ok)\\s*$");  // hoved-knap
            clickD(0, "(?i)^\\s*(open|åbn|aabn|done|f(ae|æ)rdig|udf(oe|ø)rt)\\s*$");        // afslut
            sleep(1000);
        }
        return "OK";
    }

    String gettextD(final int d, final String regex) {  // returnerer foerste match-tekst eller "NONE"
        final Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return onMain(new Job() { public String run() {
            return matchText(d, p);
        } }, 6000);
    }

    String scrollD(final int d) {
        return onMain(new Job() { public String run() {
            AccessibilityNodeInfo s = findScrollable(d);
            if (s == null) return "NONE";
            boolean ok = s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            return ok ? "OK" : "ERR scroll-false";
        } }, 4000);
    }

    String launchD(final int d, final String action) {
        return onMain(new Job() { public String run() {
            try {
                Intent intent = new Intent(action);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchDisplayId(d);
                startActivity(intent, opts.toBundle());
                return "OK";
            } catch (Throwable t) { return "ERR " + t; }
        } }, 4000);
    }

    // ---------------- WD-recovery (port af meeting-camera/wd-up.sh, in-process) ----------------
    //
    // Taender "Traadloes fejlfinding" via Settings-UI (det ENESTE der virker uden root paa denne
    // Samsung A12 - et globalt adb_wifi_enabled=1-skriv taender IKKE listeneren, se wd-keepalive-
    // spike) og laeser den nye ip:port direkte fra skaermen. Returnerer "ip:port" eller null.
    // Koeres paa en arbejder-traad (IKKE main) - de enkelte node-ops broer selv til main via onMain.
    // Display 0 = telefonskaermen; kraever vaaken skaerm (stay_on_while_plugged_in=7).
    // Aktiver Udviklerindstillinger automatisk (a11y-drevet "tap Build-nummer 7x"), ligesom WD-recovery
    // driver Settings-UI'et. Springer over hvis allerede aktiveret. Samsung One UI nester Build-nummer
    // under "Software information". Kraever enheden en lock-credential (PIN/moenster/kode) efter tap'ene,
    // kan a11y ikke indtaste den -> afbryder + logger (det ene trin er saa engangs-manuelt).
    // probeOnly=true: navigér + rapportér om Build-nummer-raekken findes UDEN at taste (sikkert naar Dev
    // Options allerede er paa). Returnerer true hvis Dev Options er (blevet) aktiveret / fundet ved probe.
    boolean ensureDeveloperOptions(boolean probeOnly) {
        final int d = 0;
        if (!probeOnly) {
            try {
                if (Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
                    Log.i(TAG, "dev-options: allerede aktiveret"); return true;
                }
            } catch (Throwable t) { }
        }
        final String bn = "build.?number|byggenummer|build.?nummer|versionsnummer";
        Log.i(TAG, "dev-options: aabner Om telefonen");
        launchD(d, "android.settings.DEVICE_INFO_SETTINGS");
        sleep(2000);
        // Samsung: Build-nummer ligger under "Software information". Gaa derind hvis raekken findes.
        for (int i = 0; i < 6; i++) {
            if ("1".equals(stateD(d, bn))) break;
            if ("1".equals(stateD(d, "software information|softwareoplysninger"))) {
                clickD(d, "software information|softwareoplysninger"); sleep(1500); break;
            }
            scrollD(d); sleep(700);
        }
        boolean found = false;
        for (int i = 0; i < 8; i++) {
            if ("1".equals(stateD(d, bn))) { found = true; break; }
            scrollD(d); sleep(700);
        }
        if (!found) { Log.w(TAG, "dev-options: fandt ikke Build-nummer-raekken"); return false; }
        if (probeOnly) { Log.i(TAG, "dev-options: probe OK - Build-nummer fundet"); return true; }
        Log.i(TAG, "dev-options: tapper Build-nummer (op til 8x)");
        final String pin = "enter.*pin|indtast.*pin|enter.*pattern|tegn.*m.nster|enter.*password|indtast.*adgangskode|confirm.*pin|bekr.ft.*pin";
        for (int i = 0; i < 8; i++) {
            clickD(d, bn);
            sleep(800);
            if ("1".equals(stateD(d, pin))) {
                Log.w(TAG, "dev-options: enheden kraever lock-credential - kan ikke automatiseres (engangs-manuel)");
                return false;
            }
            try { if (Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) break; } catch (Throwable t) { }
        }
        boolean ok;
        try { ok = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1; }
        catch (Throwable t) { ok = false; }
        Log.i(TAG, "dev-options: " + (ok ? "aktiveret" : "ikke aktiveret"));
        return ok;
    }

    String recoverWirelessDebugging() {
        ensureDeveloperOptions(false);   // automatisk 7-tap hvis Dev Options ikke er paa (ellers no-op)
        final int d = 0;
        final Pattern ipPort = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+:[0-9]+");

        Log.i(TAG, "wd-recovery: aabner udviklingsindstillinger");
        launchD(d, "android.settings.APPLICATION_DEVELOPMENT_SETTINGS");
        sleep(2000);

        // Scroll til "Wireless debugging"-raekken. ^-anker undgaar statusbar-notifikationen.
        boolean found = false;
        for (int i = 0; i < 12; i++) {
            if ("1".equals(stateD(d, "^wireless debugging|^traadloes fejlfinding"))) { found = true; break; }
            scrollD(d);
            sleep(1000);
        }
        if (!found) { Log.w(TAG, "wd-recovery: fandt ikke WD-raekken"); return cachedOrNull(); }
        clickD(d, "^wireless debugging|^traadloes fejlfinding");
        sleep(2000);

        // Kan vi allerede laese en ip:port -> WD er TIL. Vi slaar ALDRIG fra.
        String ipp = readIpPort(d, ipPort);
        if (ipp != null) { Log.i(TAG, "wd-recovery: allerede TIL " + ipp); return remember(ipp); }

        Log.i(TAG, "wd-recovery: ingen ip:port -> taender toggle");
        clickD(d, "use wireless debugging|brug traadloes fejlfinding");
        sleep(3000);

        // Bekraeft "Allow ...?"-dialogen. Match knappen PRAECIST (^allow$/^tillad$) - et bredt
        // 'allow' rammer ellers dialog-TITLEN. Husk valget via checkboxen.
        if ("1".equals(stateD(d, "allow wireless debugging|tillad traadloes fejlfinding"))) {
            clickD(d, "always allow on this network|altid tillad paa dette netvaerk");
            sleep(1000);
            clickD(d, "^allow$|^tillad$");
            sleep(3000);
        }
        for (int i = 0; i < 8; i++) {
            ipp = readIpPort(d, ipPort);
            if (ipp != null) break;
            sleep(1000);
        }
        if (ipp == null) { Log.w(TAG, "wd-recovery: kunne ikke laese ip:port"); return cachedOrNull(); }
        Log.i(TAG, "wd-recovery: laeste " + ipp);
        return remember(ipp);
    }

    // WD-parring for en NY adb-host (fx en frisk PC): aabn "Pair device with pairing code"-dialogen
    // og laes parrings-adresse (ip:port) + 6-cifret kode fra skaermen. Returnerer "ip:port kode" eller
    // null. Bruges af companion-install (engangs pr. PC) saa scrcpy-broen kan tilgaaes Termux-uafhaengigt.
    // Android 12's Wireless Debugging er TLS -> en ny host SKAL parres foer 'adb connect' virker.
    String startWdPairing() {
        final int d = 0;
        launchD(d, "android.settings.APPLICATION_DEVELOPMENT_SETTINGS");
        sleep(2000);
        boolean found = false;
        for (int i = 0; i < 12; i++) {
            if ("1".equals(stateD(d, "^wireless debugging|^traadloes fejlfinding"))) { found = true; break; }
            scrollD(d);
            sleep(1000);
        }
        if (!found) { Log.w(TAG, "wd-pair: fandt ikke WD-raekken"); return null; }
        clickD(d, "^wireless debugging|^traadloes fejlfinding");
        sleep(2000);

        // Aabn parrings-dialogen (scroll lidt hvis raekken ikke ses).
        for (int i = 0; i < 5; i++) {
            if ("1".equals(stateD(d, "pair device with pairing code|pairing code|parringskode"))) break;
            scrollD(d);
            sleep(800);
        }
        clickD(d, "pair device with pairing code|parring af enhed med parringskode|pairing code");
        sleep(2500);

        // Laes kode (et tekst-node der ER praecis 6 cifre) + parrings-adresse (ip:port).
        String code = gettextD(d, "^[0-9]{6}$");
        String addr = gettextD(d, "[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+:[0-9]+");
        if (code == null || code.equals("NONE") || addr == null || addr.equals("NONE")) {
            Log.w(TAG, "wd-pair: kunne ikke laese kode/adresse");
            return null;
        }
        Log.i(TAG, "wd-pair: dialog aaben, adresse " + addr);
        return addr + " " + code;
    }

    private String readIpPort(int d, Pattern ipPort) {
        String t = gettextD(d, ipPort.pattern());
        if (t == null || t.equals("NONE")) return null;
        Matcher m = ipPort.matcher(t);
        return m.find() ? m.group() : null;
    }

    private String remember(String ipp) { Rig.lastWdIpPort = ipp; return ipp; }
    private String cachedOrNull() { return Rig.lastWdIpPort.isEmpty() ? null : Rig.lastWdIpPort; }
    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    // ---------------- DeX-reconnect (port af meeting-camera/lib.sh::ensure_dex_up, adb-frit) ----------------
    //
    // Sikr at Samsung DeX (display 2) er oppe. Kaldes paa boot/recovery naar Rig.dexReconnect=true.
    // Quick-settings aabnes via performGlobalAction (a11y) i stedet for adb 'cmd statusbar'. Returnerer
    // true hvis DeX er oppe. Bemaerk: skaerm-wake forudsaetter stay-awake-while-charging (display 0
    // taendt); a11y kan ikke selv taende en slukket skaerm. Returnerer hurtigt hvis DeX allerede er oppe.
    boolean ensureDexUp() {
        final int dex = 2;
        if ("1".equals(stateD(dex, "."))) return true;   // DeX-displayet har allerede noder -> oppe
        Log.i(TAG, "dex-up: DeX nede - forsoeger at taende via quick-settings");
        if (Build.VERSION.SDK_INT >= 31) {
            onMain(new Job() { public String run() { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS); return "OK"; } }, 3000);
        }
        sleep(1500);
        clickD(0, "^dex,|samsung dex");          // DeX-tile (desc fx "DeX, Off., Button")
        sleep(2000);
        if ("1".equals(stateD(0, "samsung dex|start dex"))) {   // evt. valg-popup
            clickD(0, "samsung dex|start dex");
            sleep(2000);
        }
        onMain(new Job() { public String run() { performGlobalAction(GLOBAL_ACTION_BACK); return "OK"; } }, 2000);
        for (int i = 0; i < 12; i++) { if ("1".equals(stateD(dex, "."))) break; sleep(2000); }
        boolean up = "1".equals(stateD(dex, "."));
        Log.i(TAG, "dex-up: DeX " + (up ? "oppe" : "kom IKKE op"));
        return up;
    }

    // ---------------- RPC-ops (port af DexRPC's TapService, samme semantik/svar) ----------------

    private String listDisplays() {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] ds = dm.getDisplays();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ds.length; i++) {
            if (sb.length() > 0) sb.append(",");
            sb.append(ds[i].getDisplayId()).append(":").append(ds[i].getRotation());
        }
        return sb.toString();
    }

    private String rotationOf(int d) {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display disp = dm.getDisplay(d);
        if (disp == null) return "ERR no-display";
        return String.valueOf(disp.getRotation());
    }

    private String doScrollRpc(int displayId, boolean back) {
        AccessibilityNodeInfo s = findScrollable(displayId);
        if (s == null) return "NONE no-scrollable";
        int action = back ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                          : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        boolean ok = s.performAction(action);
        return ok ? "OK scroll" : "ERR scroll-false";
    }

    private String doGlobal(String name) {
        int a;
        if (name.equals("back")) a = GLOBAL_ACTION_BACK;
        else if (name.equals("home")) a = GLOBAL_ACTION_HOME;
        else if (name.equals("recents")) a = GLOBAL_ACTION_RECENTS;
        else if (name.equals("notifications")) a = GLOBAL_ACTION_NOTIFICATIONS;
        else if (name.equals("quicksettings")) a = GLOBAL_ACTION_QUICK_SETTINGS;
        else return "ERR unknown-action";
        boolean ok = performGlobalAction(a);
        return ok ? "OK" : "ERR false";
    }

    private String doLaunch(int d, String action, String data, String pkg) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (data != null && !data.equals("-")) intent.setData(Uri.parse(data));
            if (pkg != null && !pkg.equals("-")) intent.setPackage(pkg);
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(d);
            startActivity(intent, opts.toBundle());
            return "OK launched";
        } catch (Throwable t) {
            return "ERR " + t;
        }
    }

    private String dumpDisplay(int displayId) {
        List<AccessibilityWindowInfo> wins = windowsForDisplay(displayId);
        if (wins == null) return "NONE (no windows on display " + displayId + ")";
        StringBuilder sb = new StringBuilder();
        int[] count = new int[]{0};
        for (int i = 0; i < wins.size(); i++) {
            AccessibilityWindowInfo w = wins.get(i);
            if (w == null) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            dumpNode(root, sb, count);
        }
        if (sb.length() == 0) return "NONE (empty)";
        return sb.toString().trim();
    }

    private void dumpNode(AccessibilityNodeInfo n, StringBuilder sb, int[] count) {
        if (n == null || count[0] > 400) return;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        boolean interesting = (t != null && t.length() > 0) || (d != null && d.length() > 0) || n.isClickable();
        if (interesting) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            sb.append("[").append(n.getClassName()).append("] ")
              .append("t='").append(t == null ? "" : t).append("' ")
              .append("d='").append(d == null ? "" : d).append("' ")
              .append(r.left).append(",").append(r.top).append(",").append(r.right).append(",").append(r.bottom)
              .append(n.isClickable() ? " CLICK" : "")
              .append("\n");
            count[0]++;
        }
        int cc = n.getChildCount();
        for (int i = 0; i < cc; i++) dumpNode(n.getChild(i), sb, count);
    }

    private String doGesture(final int x, final int y, final int x2, final int y2, final int displayId, final int dur) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] completed = new boolean[]{false};
        final boolean[] dispatched = new boolean[]{true};
        mainHandler.post(new Runnable() {
            public void run() {
                Path p = new Path();
                p.moveTo(x, y);
                if (x2 >= 0 && y2 >= 0) p.lineTo(x2, y2);
                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(p, 0L, (long) dur);
                GestureDescription.Builder b = new GestureDescription.Builder();
                b.addStroke(stroke);
                if (Build.VERSION.SDK_INT >= 30) {
                    try { b.setDisplayId(displayId); } catch (Throwable t) { Log.e(TAG, "setDisplayId", t); }
                }
                boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) { completed[0] = true; latch.countDown(); }
                    @Override public void onCancelled(GestureDescription g) { completed[0] = false; latch.countDown(); }
                }, null);
                if (!ok) { dispatched[0] = false; latch.countDown(); }
            }
        });
        try { latch.await(5000, TimeUnit.MILLISECONDS); } catch (InterruptedException e) { return "ERR interrupted"; }
        if (!dispatched[0]) return "ERR dispatch-false";
        return completed[0] ? "OK" : "ERR cancelled";
    }

    // ---------------- node-helpers (uaendret fra DexRPC's logik) ----------------

    // Vinduer for et display. API 30+: alle displays (multi-display/DeX). <30: kun det aktive
    // (default) display via getWindows() - aeldre Android (8-10) har ikke getWindowsOnAllDisplays,
    // saa multi-display/DeX falder gracefuldt bort og appen virker enkelt-display.
    private List<AccessibilityWindowInfo> windowsForDisplay(int d) {
        if (Build.VERSION.SDK_INT >= 30) {
            SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
            return all == null ? null : all.get(d);
        }
        if (d == Display.DEFAULT_DISPLAY) return getWindows();
        return null;
    }

    private String matchText(int d, Pattern pat) {
        List<AccessibilityWindowInfo> wins = windowsForDisplay(d);
        if (wins == null) return "NONE";
        for (int i = 0; i < wins.size(); i++) {
            AccessibilityWindowInfo w = wins.get(i);
            if (w == null) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            String r = dfsText(root, pat);
            if (r != null) return r;
        }
        return "NONE";
    }

    private String dfsText(AccessibilityNodeInfo n, Pattern pat) {
        if (n == null) return null;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        if (t != null && pat.matcher(t).find()) return t.toString();
        if (d != null && pat.matcher(d).find()) return d.toString();
        int cc = n.getChildCount();
        for (int i = 0; i < cc; i++) {
            String r = dfsText(n.getChild(i), pat);
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo findNode(int displayId, Pattern pat) {
        List<AccessibilityWindowInfo> wins = windowsForDisplay(displayId);
        if (wins == null) return null;
        for (int i = 0; i < wins.size(); i++) {
            AccessibilityWindowInfo w = wins.get(i);
            if (w == null) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            AccessibilityNodeInfo found = dfs(root, pat);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo dfs(AccessibilityNodeInfo n, Pattern pat) {
        if (n == null) return null;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        if ((t != null && pat.matcher(t).find()) || (d != null && pat.matcher(d).find())) return n;
        int cc = n.getChildCount();
        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo r = dfs(n.getChild(i), pat);
            if (r != null) return r;
        }
        return null;
    }

    private String performClick(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo t = n;
        int guard = 0;
        while (t != null && !t.isClickable() && guard < 25) { t = t.getParent(); guard++; }
        AccessibilityNodeInfo target = (t != null) ? t : n;
        boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return ok ? "OK click" : "ERR click-false";
    }

    private AccessibilityNodeInfo findScrollable(int displayId) {
        List<AccessibilityWindowInfo> wins = windowsForDisplay(displayId);
        if (wins == null) return null;
        for (int i = 0; i < wins.size(); i++) {
            AccessibilityWindowInfo w = wins.get(i);
            if (w == null) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            AccessibilityNodeInfo s = dfsScroll(root);
            if (s != null) return s;
        }
        return null;
    }

    private AccessibilityNodeInfo dfsScroll(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isScrollable()) return n;
        int cc = n.getChildCount();
        for (int i = 0; i < cc; i++) {
            AccessibilityNodeInfo r = dfsScroll(n.getChild(i));
            if (r != null) return r;
        }
        return null;
    }
}
