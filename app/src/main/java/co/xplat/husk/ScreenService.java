// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

// Skaerm-streaming via MediaProjection - workaround for scrcpy paa enheder UDEN Wireless Debugging
// (Android 9 og aeldre, hvor Husks adb-bro ikke kan bruges). Fanger skaermen til en VirtualDisplay
// -> ImageReader(RGBA) -> JPEG -> Rig.latestScreenJpeg, som ControlServer streamer som MJPEG over
// Tailscale. Kombineret med klik->a11y-tap (8127) giver det se+styr i browseren, no-root, no-USB.
//
// Samtykke: MediaProjection kraever en engangs-"Start nu"-dialog pr. session (sikkerhed). MainActivity
// henter token'et via startActivityForResult og starter denne service med det. START_NOT_STICKY:
// projektionen kan ikke gen-startes uden nyt samtykke, saa vi auto-genstarter ikke.
public class ScreenService extends Service {
    static final String TAG = "Husk";
    static final String CHANNEL = "rig-screen";
    static final int NOTIF_ID = 43;
    static final int MAX_W = 720;          // skaler ned for baandbredde (capture-bredde; fast pr. session)

    private MediaProjection projection;
    private VirtualDisplay vdisplay;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private volatile boolean started = false;
    private long lastFrameMs = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        Rig.appContext = getApplicationContext();
        Rig.loadMotionPrefs(getApplicationContext());   // bevaegelses-alarm-config (motion + ntfy) fra prefs
        Rig.ensureControlServer();   // 8090 op uafhaengigt af kameraet -> /screen + /control virker uden kamera
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Husk")
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && intent.hasExtra("resultCode") && !started) {
            started = true;
            final int code = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            final Intent data = intent.getParcelableExtra("data");
            thread = new HandlerThread("screen-bg");
            thread.start();
            handler = new Handler(thread.getLooper());
            handler.post(new Runnable() { public void run() { startCapture(code, data); } });
        }
        return START_NOT_STICKY;
    }

    private void startCapture(int code, Intent data) {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) { Log.e(TAG, "screen: ingen MediaProjection (samtykke afvist?)"); return; }
            // Android 14 (target 34) kraever en registreret callback FOER createVirtualDisplay.
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { Rig.screenRunning = false; }
            }, handler);
            // VIGTIGT: brug den FULDE fysiske skaerm (getRealMetrics), ikke getDisplayMetrics() (= app-omraadet
            // uden nav-baren). a11y-dispatchGesture arbejder i det fulde skaerm-rum; bruger vi app-omraadet bliver
            // /screen-billedet lodret presset ift. gesture-rummet -> /control- og tap-koordinater rammer for hoejt.
            DisplayMetrics m = new DisplayMetrics();
            try {
                android.view.WindowManager wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
                wm.getDefaultDisplay().getRealMetrics(m);
            } catch (Throwable t) { m = getResources().getDisplayMetrics(); }
            int w = m.widthPixels, h = m.heightPixels, dpi = m.densityDpi;
            int sw = w, sh = h;
            if (w > MAX_W) { sw = MAX_W; sh = (int) ((long) h * MAX_W / w); }
            Rig.screenW = w; Rig.screenH = h;   // a11y-tap bruger de RIGTIGE skaerm-px
            reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2);
            final int fsw = sw, fsh = sh;
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader r) { onFrame(r, fsw, fsh); }
            }, handler);
            vdisplay = projection.createVirtualDisplay("husk-screen", sw, sh, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            Rig.screenRunning = true;   // skaermdeling koerer reelt nu
            // Eksponér projection + downscalede dims, saa H264Stream (opt-in /screen.mp4) kan lave sit eget
            // VirtualDisplay paa samme projection til hardware-H.264. Roerer ikke JPEG-/MJPEG-vejen.
            Rig.mediaProjection = projection; Rig.capW = sw; Rig.capH = sh; Rig.capDpi = dpi;
            Log.i(TAG, "screen: capture " + sw + "x" + sh + " (rigtig " + w + "x" + h + ")");
        } catch (Throwable t) {
            Log.e(TAG, "startCapture", t);
        }
    }

    private void onFrame(ImageReader r, int sw, int sh) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastFrameMs < Rig.screenMinFrameMs) return;   // throttle (live-justerbar via /set?sfps=)
            lastFrameMs = now;
            Image.Plane p = img.getPlanes()[0];
            ByteBuffer buf = p.getBuffer();
            int pixelStride = p.getPixelStride();
            int rowStride = p.getRowStride();
            int rowPadding = rowStride - pixelStride * sw;
            int bmpW = sw + (pixelStride > 0 ? rowPadding / pixelStride : 0);
            Bitmap bmp = Bitmap.createBitmap(bmpW, sh, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            Bitmap out = (bmpW == sw) ? bmp : Bitmap.createBitmap(bmp, 0, 0, sw, sh);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(48 * 1024);
            out.compress(Bitmap.CompressFormat.JPEG, Math.max(1, Math.min(100, Rig.screenQuality)), bos);
            Rig.latestScreenJpeg = bos.toByteArray();
            Rig.latestScreenSeq++;
            if (Motion.shouldSample()) Motion.feed(out, "screen");   // bevaegelses-alarm paa skaerm-feed
            if (out != bmp) out.recycle();
            bmp.recycle();
        } catch (Throwable t) {
            Log.e(TAG, "screen onFrame", t);
        } finally {
            if (img != null) img.close();
        }
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Husk-skaerm", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        try { if (vdisplay != null) vdisplay.release(); } catch (Throwable ignored) {}
        try { if (reader != null) reader.close(); } catch (Throwable ignored) {}
        try { if (projection != null) projection.stop(); } catch (Throwable ignored) {}
        try { if (thread != null) thread.quitSafely(); } catch (Throwable ignored) {}
        try { Rig.stopH264(); } catch (Throwable ignored) {}
        Rig.mediaProjection = null;
        Rig.latestScreenJpeg = null;
        Rig.screenRunning = false;
        super.onDestroy();
    }
}
