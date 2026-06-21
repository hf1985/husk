// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Indbygget opdatering: henter version-info fra xplat.co/husk/latest.json, sammenligner med den
// installerede versionCode, downloader APK'en og installerer via PackageInstaller (REN framework -
// ingen FileProvider/AndroidX). Kraever REQUEST_INSTALL_PACKAGES + at brugeren har givet "installer
// ukendte apps" for Husk (systemet beder selv om det foerste gang via samtykke-dialogen).
public final class Updater {
    static final String TAG = "Husk";
    static final String INSTALL_ACTION = "co.xplat.husk.INSTALL_STATUS";

    // Version-kilder, proeves i raekkefoelge. xplat.co's cert kaeder til Google Trust Services, hvis
    // krydssignering Android 9's system-trust-store ofte IKKE kan bygge (handshake-fejl) - derfor faldes
    // tilbage til raw.githubusercontent.com, der bruger Let's Encrypt / ISRG Root X1 (indbygget i Android
    // 7.1.1+). APK'en ligger paa objects.githubusercontent.com (ogsaa ISRG) -> Android-9-betroet hele vejen.
    static final String[] SOURCES = {
        "https://xplat.co/husk/latest.json",
        "https://raw.githubusercontent.com/hf1985/husk/main/latest.json"
    };

    private Updater() {}

    public static void checkAndUpdate(final Context ctx) {
        final Handler main = new Handler(Looper.getMainLooper());
        Rig.lastUpdate = "checking";
        toast(main, ctx, ctx.getString(R.string.update_checking));
        new Thread(new Runnable() { public void run() {
            try {
                int cur = currentVersionCode(ctx);
                JSONObject j = null; String src = null; StringBuilder errs = new StringBuilder();
                for (String url : SOURCES) {
                    String tag = url.contains("xplat") ? "xplat" : "github";
                    try { j = new JSONObject(httpGet(url)); src = tag; break; }
                    catch (Throwable e) { errs.append(tag).append("=").append(e.getClass().getSimpleName()).append("; "); }
                }
                if (j == null) {
                    Rig.lastUpdate = "ERR alle kilder fejlede: " + errs;
                    toast(main, ctx, ctx.getString(R.string.update_failed) + " (" + errs + ")");
                    return;
                }
                int latest = j.getInt("versionCode");
                String apk = j.getString("apk");
                String ver = j.optString("versionName", "");
                if (latest <= cur) {
                    Rig.lastUpdate = "latest via " + src + " (have " + cur + ", remote " + latest + ")" + (errs.length() > 0 ? " [" + errs + "]" : "");
                    toast(main, ctx, ctx.getString(R.string.update_latest));
                    return;
                }
                Rig.lastUpdate = "downloading " + ver + " via " + src;
                toast(main, ctx, ctx.getString(R.string.update_downloading) + " " + ver);
                installApk(ctx, httpGetBytes(apk));
                Rig.lastUpdate = "install requested " + ver + " via " + src;
            } catch (Throwable t) {
                // Eksponer den PRAECISE aarsag (klasse + besked) via Rig.lastUpdate -> /flags, saa den
                // kan laeses remote over Tailscale. SSLHandshakeException = MITM; UnknownHost = DNS; osv.
                Rig.lastUpdate = "ERR " + t.getClass().getSimpleName() + ": " + t.getMessage();
                Log.e(TAG, "update", t);
                toast(main, ctx, ctx.getString(R.string.update_failed) + " (" + t.getClass().getSimpleName() + ")");
            }
        } }, "husk-update").start();
    }

    private static int currentVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
        } catch (Throwable t) { return 0; }
    }

    private static void installApk(Context ctx, byte[] apk) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
            new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sid = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(sid);
        OutputStream out = session.openWrite("husk.apk", 0, apk.length);
        out.write(apk);
        session.fsync(out);
        out.close();
        Intent intent = new Intent(INSTALL_ACTION).setPackage(ctx.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent pending = PendingIntent.getBroadcast(ctx, sid, intent, flags);
        session.commit(pending.getIntentSender());   // -> systemets install-bekraeftelse (via InstallReceiver)
        session.close();
    }

    static String httpGet(String urlStr) throws Exception {
        return new String(httpGetBytes(urlStr), "UTF-8");
    }

    static byte[] httpGetBytes(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 Husk");
        try {
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {           // foelg cross-host redirect manuelt (GitHub-assets)
                String loc = c.getHeaderField("Location");
                if (loc != null) { c.disconnect(); return httpGetBytes(loc); }
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static void toast(Handler main, final Context ctx, final String msg) {
        main.post(new Runnable() { public void run() { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show(); } });
    }
}
