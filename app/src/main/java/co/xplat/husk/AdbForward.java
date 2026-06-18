// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

// Raa TCP-bro: eksponerer enhedens egen adbd (Wireless Debugging) paa Tailscale-IP'en, saa
// scrcpy/adb-over-Tailscale bliver en DEL AF APPEN - Termux-UAFHAENGIGT. Ingen socat, ingen
// Termux-adb-server, ingen Termux:Boot-hook: PC'en kan 'adb connect <tailscale>:5557' direkte
// mod adbd via denne bro (appen broer Tailscale-IP:5557 -> 127.0.0.1:<WD-port>, hvor adbd lytter
// paa loopback). WD-porten skifter ved reboot; vi henter den fra Rig.lastWdIpPort (appens egen
// in-process WD-recovery cacher den; tom -> trigger recovery).
//
// SIKKERHED: samme posture som den gamle socat-forward - en adb-forbindelse = fuld enhedskontrol.
// Broen binder KUN Tailscale-IP'en (aldrig 0.0.0.0/LAN), saa fjernfladen daekkes af Tailscale-ACL'en
// (manuelt admin-konsol-punkt: begraens 5557 til PC'en). adbd's egen RSA-noegle-godkendelse gaelder
// stadig pr. host (PC'ens noegle godkendes een gang; RigAccessibilityService kan auto-acceptere dialogen).
public class AdbForward {
    static final String TAG = "Husk";
    static final int LISTEN_PORT = 5557;

    private volatile boolean running = false;
    private ServerSocket server;

    public void start(final String tsIp) {
        if (tsIp == null) { Log.w(TAG, "adb-forward: ingen Tailscale-IP -> bro ikke startet"); return; }
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName(tsIp), LISTEN_PORT), 16);
        } catch (Throwable t) {
            Log.e(TAG, "adb-forward kunne ikke binde " + tsIp + ":" + LISTEN_PORT, t);
            return;
        }
        running = true;
        Log.i(TAG, "adb-forward lytter paa " + tsIp + ":" + LISTEN_PORT + " -> 127.0.0.1:<WD>");
        Thread th = new Thread(new Runnable() { public void run() { acceptLoop(); } }, "rig-adbfwd");
        th.setDaemon(true);
        th.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket client = server.accept();
                Thread t = new Thread(new Runnable() { public void run() { handle(client); } }, "rig-adbfwd-conn");
                t.setDaemon(true);
                t.start();
            } catch (Throwable t) {
                if (running) Log.e(TAG, "adb-forward accept", t);
                else break;
            }
        }
    }

    // Find adbd's aktuelle WD-port. Appens in-process WD-recovery cacher ip:port i Rig.lastWdIpPort;
    // er den tom (endnu ingen /wd kaldt), trigger vi recovery (taender WD + laeser porten).
    private int wdPort() {
        String ipp = Rig.lastWdIpPort;
        if ((ipp == null || ipp.isEmpty()) && Rig.a11y != null) {
            String r = Rig.a11y.recoverWirelessDebugging();
            if (r != null) ipp = r;
        }
        if (ipp == null || ipp.indexOf(':') < 0) return -1;
        try { return Integer.parseInt(ipp.substring(ipp.indexOf(':') + 1)); } catch (Throwable t) { return -1; }
    }

    private void handle(Socket client) {
        Socket up = null;
        try {
            int port = wdPort();
            if (port < 0) { Log.w(TAG, "adb-forward: ukendt WD-port -> lukker forbindelse"); client.close(); return; }
            up = new Socket();
            up.connect(new InetSocketAddress("127.0.0.1", port), 8000);
            final Socket fUp = up, fCl = client;
            Thread up2cl = new Thread(new Runnable() { public void run() { pump(fUp, fCl); } }, "rig-adbfwd-up");
            up2cl.setDaemon(true);
            up2cl.start();
            pump(fCl, fUp);   // client -> adbd paa denne traad
        } catch (Throwable t) {
            Log.e(TAG, "adb-forward handle", t);
        } finally {
            try { client.close(); } catch (Throwable ig) { }
            try { if (up != null) up.close(); } catch (Throwable ig) { }
        }
    }

    private void pump(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); out.flush(); }
        } catch (Throwable ignored) {
            // peer lukkede - normalt ved scrcpy-luk
        } finally {
            try { to.shutdownOutput(); } catch (Throwable ig) { }
        }
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ig) { }
    }
}
