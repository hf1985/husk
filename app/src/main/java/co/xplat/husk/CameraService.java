// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

// Foreground-service (type=camera) der holder kameraet aabent og leverer JPEG-frames til Rig.
// foregroundServiceType=camera er forsoeget paa at overleve screen-off (Spike 1 (a)); falder
// det, er den dedikerede skaerm-taendte MainActivity (Spike 1 (b)) en accepteret driftsform.
//
// Capture-vej (minimal prototype, kun framework-API'er - INGEN CameraX/AndroidX, saa den bygger
// med samme ecj/dx/aapt2-pipeline som DexRPC): Camera2 + ImageReader(JPEG). Rotation gives gratis
// via CaptureRequest.JPEG_ORIENTATION; horisontal flip kun naar slaaet til (re-encode pr. frame).
// Hvis JPEG-i-repeating-preview giver for lav fps paa enheden, er opgraderingen YUV_420_888 +
// YuvImage.compressToJpeg (noteret i README som Spike-2-trin).
public class CameraService extends Service {
    static final String TAG = "Husk";
    static final String CHANNEL = "rig-camera";
    static final int NOTIF_ID = 42;

    private HandlerThread camThread;
    private Handler camHandler;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean started = false;
    private volatile boolean destroyed = false;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        Rig.appContext = getApplicationContext();
        Rig.loadMotionPrefs(getApplicationContext());   // bevaegelses-alarm-config (motion + ntfy) fra prefs
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("Husk")
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIF_ID, n);
        }
        // Partial wakelock: hold CPU'en vaagen saa capture+server koerer screen-off (Spike 1 (a)).
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "husk:camera");
        wakeLock.setReferenceCounted(false);
        try { wakeLock.acquire(); } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String tok = intent.getStringExtra("token");
            if (tok != null) Rig.token = tok;
            if (intent.hasExtra("rotation")) Rig.rotation = intent.getIntExtra("rotation", 0);
            if (intent.hasExtra("flip"))     Rig.flip = intent.getBooleanExtra("flip", false);
            if (intent.hasExtra("front"))    Rig.useFront = intent.getBooleanExtra("front", false);
            if (intent.hasExtra("fps"))      Rig.targetFps = intent.getIntExtra("fps", 10);
        }
        if (!started) {
            started = true;
            camThread = new HandlerThread("cam-bg");
            camThread.start();
            camHandler = new Handler(camThread.getLooper());
            startServer();
            camHandler.post(new Runnable() { public void run() { openCamera(); } });
            maybeReconnectDex();
            maybeAutoScreenShare();
        }
        return START_STICKY;   // genstart hvis systemet draeber os (recovery-venlig)
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Rig-kamera", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private void startServer() {
        try {
            Rig.ensureControlServer();   // proces-singleton; lever uafhaengigt af kameraet (skaerm + /control)
        } catch (Throwable t) {
            Log.e(TAG, "control-server start fejlede", t);
        }
    }

    // Permanent skaermdeling: hvis brugeren har slaaet den til (pref), gen-etabler den efter boot/start.
    // Forsinket saa a11y + Tailscale er oppe; a11y tapper selv "Start nu"-samtykket via ScreenConsentActivity.
    // Springes over hvis allerede koerende. (Kun naar pref er sat -> rig'en der kun bruger kamera roeres ikke.)
    private void maybeAutoScreenShare() {
        boolean want = getSharedPreferences("husk", Context.MODE_PRIVATE).getBoolean("screen_share", false);
        if (!want) return;
        camHandler.postDelayed(new Runnable() {
            int tries = 0;
            public void run() {
                if (Rig.screenRunning) return;
                if (Rig.a11y != null) {
                    try {
                        Intent i = new Intent(CameraService.this, ScreenConsentActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable t) { Log.e(TAG, "auto-screenshare", t); }
                    return;
                }
                if (++tries < 8) camHandler.postDelayed(this, 5000);
            }
        }, 18000);
    }

    // DeX-reconnect ved boot/start: laes brugerens toggle og bring DeX op via a11y hvis slaaet til.
    // Forsinket (15s) saa a11y-servicen er bundet + DeX kan naa op efter boot; faa retries hvis a11y
    // endnu ikke er klar. Kun relevant naar DeX-toggle er TIL (default fra; skjult paa ikke-DeX).
    private void maybeReconnectDex() {
        Rig.dexReconnect = getSharedPreferences("husk", Context.MODE_PRIVATE).getBoolean("dex_reconnect", false);
        if (!Rig.dexReconnect) return;
        camHandler.postDelayed(new Runnable() {
            int tries = 0;
            public void run() {
                RigAccessibilityService svc = Rig.a11y;
                if (svc != null) { svc.ensureDexUp(); return; }
                if (++tries < 6) camHandler.postDelayed(this, 5000);
            }
        }, 15000);
    }

    // ---------------- Camera2 ----------------

    // Watchdog: hvis OS'et river kameraet fra os (onDisconnected/onError), genaabn med backoff i stedet for
    // at lade feed'et fryse tavst (enheden saa "oppe" men /snapshot frosset). Stopper naar servicen destrueres.
    private void scheduleReopen() {
        if (destroyed || camHandler == null) return;
        camHandler.postDelayed(new Runnable() { public void run() {
            if (destroyed || Rig.cameraRunning || cameraDevice != null) return;
            Log.i(TAG, "kamera reopen-forsoeg");
            try { openCamera(); } catch (Throwable t) { Log.e(TAG, "reopen", t); scheduleReopen(); }
        } }, 5000);
    }

    private void openCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CAMERA-permission ikke givet - kan ikke aabne kamera (kor 'pm grant')");
            return;
        }
        try {
            try { if (imageReader != null) { imageReader.close(); imageReader = null; } } catch (Throwable ignored) {}   // undgaa ImageReader-laek ved reopen
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String camId = pickCamera(cameraManager, Rig.useFront);
            if (camId == null) { Log.e(TAG, "intet kamera fundet"); return; }
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(camId);
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size size = pickSize(map.getOutputSizes(ImageFormat.JPEG));
            Log.i(TAG, "aabner kamera " + camId + " @ " + size.getWidth() + "x" + size.getHeight());

            imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader r) { onFrame(r); }
            }, camHandler);

            cameraManager.openCamera(camId, new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice device) { cameraDevice = device; createSession(); }
                public void onDisconnected(CameraDevice device) { Log.w(TAG, "kamera disconnected"); Rig.cameraRunning = false; device.close(); cameraDevice = null; scheduleReopen(); }
                public void onError(CameraDevice device, int error) { Log.e(TAG, "kamera-fejl " + error); Rig.cameraRunning = false; device.close(); cameraDevice = null; scheduleReopen(); }
            }, camHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera", e);
        } catch (SecurityException e) {
            Log.e(TAG, "openCamera security", e);
        }
    }

    private void createSession() {
        try {
            cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    public void onConfigured(CameraCaptureSession session) {
                        captureSession = session;
                        startRepeating();
                    }
                    public void onConfigureFailed(CameraCaptureSession session) {
                        Log.e(TAG, "session-config fejlede");
                    }
                }, camHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "createSession", e);
        }
    }

    private void startRepeating() {
        try {
            CaptureRequest.Builder b = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b.addTarget(imageReader.getSurface());
            b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            b.set(CaptureRequest.JPEG_ORIENTATION, normRot(Rig.rotation));
            captureSession.setRepeatingRequest(b.build(), null, camHandler);
            Rig.cameraRunning = true;   // capture koerer reelt nu -> status/flags maa vise "on"
            Log.i(TAG, "repeating capture i gang");
        } catch (CameraAccessException e) {
            Log.e(TAG, "startRepeating", e);
        }
    }

    private int normRot(int r) { int v = ((r % 360) + 360) % 360; return (v / 90) * 90; }

    private void onFrame(ImageReader r) {
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            if (Rig.flip) data = flipJpeg(data);
            Rig.latestJpeg = data;
            Rig.latestSeq++;
            if (Motion.shouldSample()) {                  // bevaegelses-alarm paa kamera-feed
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 8;                       // afkod lille kun til analyse (billigt)
                Bitmap small = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (small != null) { Motion.feed(small, "camera"); small.recycle(); }
            }
        } catch (Throwable t) {
            Log.e(TAG, "onFrame", t);
        } finally {
            if (img != null) img.close();
        }
    }

    // Horisontal spejling: kun naar Rig.flip - dekod/spejl/re-encode (CPU-tungt, derfor opt-in).
    private byte[] flipJpeg(byte[] jpeg) {
        try {
            Bitmap bm = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            if (bm == null) return jpeg;
            Matrix m = new Matrix();
            m.preScale(-1f, 1f);
            Bitmap flipped = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, false);
            ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length);
            flipped.compress(Bitmap.CompressFormat.JPEG, 80, out);
            bm.recycle();
            if (flipped != bm) flipped.recycle();
            return out.toByteArray();
        } catch (Throwable t) {
            return jpeg;
        }
    }

    private static String pickCamera(CameraManager cm, boolean front) throws CameraAccessException {
        String fallback = null;
        int want = front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : cm.getCameraIdList()) {
            if (fallback == null) fallback = id;
            Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (f != null && f == want) return id;
        }
        return fallback;
    }

    // Vaelg en frame-stoerrelse taet paa 1280x720 (god balance for moede+overvaagning).
    private static Size pickSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);
        Size best = sizes[0];
        long target = 1280L * 720L, bestDiff = Long.MAX_VALUE;
        for (Size s : sizes) {
            long diff = Math.abs((long) s.getWidth() * s.getHeight() - target);
            if (diff < bestDiff) { bestDiff = diff; best = s; }
        }
        return best;
    }

    @Override
    public void onDestroy() {
        destroyed = true;            // stop reopen-watchdog
        Rig.cameraRunning = false;   // kamera stoppes -> status/flags maa vise "off" (ControlServer lever videre)
        try { if (captureSession != null) captureSession.close(); } catch (Throwable ignored) {}
        try { if (cameraDevice != null) cameraDevice.close(); } catch (Throwable ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch (Throwable ignored) {}
        try { if (camThread != null) camThread.quitSafely(); } catch (Throwable ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
