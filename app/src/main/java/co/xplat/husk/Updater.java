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
    static final String LATEST = "https://xplat.co/husk/latest.json";
    static final String INSTALL_ACTION = "co.xplat.husk.INSTALL_STATUS";

    private Updater() {}

    public static void checkAndUpdate(final Context ctx) {
        final Handler main = new Handler(Looper.getMainLooper());
        toast(main, ctx, ctx.getString(R.string.update_checking));
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject j = new JSONObject(httpGet(LATEST));
                int latest = j.getInt("versionCode");
                String apk = j.getString("apk");
                String ver = j.optString("versionName", "");
                if (latest <= currentVersionCode(ctx)) { toast(main, ctx, ctx.getString(R.string.update_latest)); return; }
                toast(main, ctx, ctx.getString(R.string.update_downloading) + " " + ver);
                installApk(ctx, httpGetBytes(apk));
            } catch (Throwable t) {
                Log.e(TAG, "update", t);
                toast(main, ctx, ctx.getString(R.string.update_failed));
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
        c.setRequestProperty("User-Agent", "Husk-updater");
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
