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
    private volatile boolean othersHaveCamera = false;   // en ANDEN app holder kameraet -> aabn ALDRIG (ingen eviction)
    private volatile boolean opening = false;            // aabning i gang (async) -> undgaa dobbelt-aabning fra demandCheck
    private volatile String  targetCamId = null;          // id'et vi vil bruge (til availability-matchning)
    private CameraManager.AvailabilityCallback availCb;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        Rig.appContext = getApplicationContext();
        Rig.loadMotionPrefs(getApplicationContext());   // bevaegelses-alarm-config (motion + ntfy) fra prefs
        createChannel();
        // FGS-type: paa Android 14 (targetSdk 34) MAA en camera-typet FGS IKKE startes fra baggrund
        // (BOOT_COMPLETED/MY_PACKAGE_REPLACED) -> selv-heal ville doe efter reboot/self-update. Vi starter
        // derfor som specialUse (servicen hoster reelt kun HTTP-serveren ved boot; kameraet er dovent) og
        // eleveR foerst til camera-FGS naar kameraet FAKTISK aabnes (openCamera.onOpened). Paa <=A13 haandhaeves
        // typen ikke fra baggrund -> vi bevarer den hidtidige camera-type dér (NUL aendring for den nuvaerende flaade).
        startFg(false);
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
            registerCameraAvailability();   // foelg om en anden app holder kameraet (saa vi ALDRIG evicter)
            camHandler.post(demandCheck);   // DOVEN: aaben/luk kameraet efter faktisk efterspoergsel + ledighed
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

    private Notification buildNotif() {
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Husk")
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true)
                .build();
    }

    // cameraActive=false: boot/idle (paa A14 = specialUse, tilladt fra baggrund). cameraActive=true: kameraet er
    // aabent (paa A14 = camera-FGS, kraevet for at bruge kameraet). Paa <=A13 er begge = camera-type (uaendret).
    // Defensivt: falder tilbage til type-loes startForeground hvis en type afvises -> servicen dor ALDRIG paa
    // startForeground (som ville tage 8090 med sig og gore selv-heal umulig).
    private void startFg(boolean cameraActive) {
        Notification n = buildNotif();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                int type = cameraActive ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
                startForeground(NOTIF_ID, n, type);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);   // <=A13: uaendret adfaerd
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable t) {
            try { startForeground(NOTIF_ID, n); } catch (Throwable t2) { Log.e(TAG, "startForeground fejlede", t2); }
        }
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

    // DOVEN kamera-styring (koerer paa camHandler hvert 1s + ved availability-event): aaben kun naar
    // (a) noget forbruger Husks feed ELLER motion er TIL, OG (b) kameraet er LEDIGT (ingen anden app).
    // Slip enheden naar efterspoergslen forsvinder, saa fx Discord-moedekameraet kan bruge kameraet
    // uforstyrret. Erstatter den gamle aggressive reopen-watchdog (der genaabnede = evictede en anden app).
    private final Runnable demandCheck = new Runnable() { public void run() {
        if (destroyed) return;
        boolean want = Rig.motionEnabled
                || (android.os.SystemClock.uptimeMillis() - Rig.lastCameraClientMs) < Rig.CAMERA_IDLE_MS;
        if (want && cameraDevice == null && !opening && !othersHaveCamera) {
            try { openCamera(); } catch (Throwable t) { Log.e(TAG, "openCamera", t); }
        } else if (!want && cameraDevice != null) {
            Log.i(TAG, "ingen kamera-forbruger -> slipper kameraet (ledigt for andre apps)");
            closeCameraDevice();
        }
        if (!destroyed && camHandler != null) camHandler.postDelayed(this, 1000);
    } };

    // Foelg systemets kamera-ledighed, saa vi ALDRIG aabner (= evicter) et kamera en anden app bruger.
    private void registerCameraAvailability() {
        try {
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try { targetCamId = pickCamera(cameraManager, Rig.useFront); } catch (Throwable ignored) {}
            availCb = new CameraManager.AvailabilityCallback() {
                @Override public void onCameraAvailable(String id) {
                    if (id.equals(targetCamId)) { othersHaveCamera = false; if (camHandler != null) camHandler.post(demandCheck); }
                }
                @Override public void onCameraUnavailable(String id) {
                    // "unavailable" gaelder ogsaa naar VI har det aabent -> kun en ANDEN app hvis vi IKKE har det.
                    if (id.equals(targetCamId) && cameraDevice == null) othersHaveCamera = true;
                }
            };
            cameraManager.registerAvailabilityCallback(availCb, camHandler);
        } catch (Throwable t) { Log.e(TAG, "registerCameraAvailability", t); }
    }

    // Luk kamera-ENHEDEN (frigiv til andre apps) UDEN at stoppe servicen/serveren (modsat onDestroy).
    private void closeCameraDevice() {
        try { if (captureSession != null) captureSession.close(); } catch (Throwable ignored) {}
        try { if (cameraDevice != null) cameraDevice.close(); } catch (Throwable ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch (Throwable ignored) {}
        captureSession = null; cameraDevice = null; imageReader = null;
        Rig.cameraRunning = false;
        Rig.latestJpeg = null;   // ryd stale frame -> /snapshot venter paa en frisk efter genaabning
        if (!destroyed) startFg(false);   // de-eleveR til specialUse (A14): kameraet er sluppet, servicen lever videre
    }

    private void openCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CAMERA-permission ikke givet - kan ikke aabne kamera (kor 'pm grant')");
            return;
        }
        if (othersHaveCamera) { Log.i(TAG, "kameraet er optaget af en anden app -> aabner IKKE (ingen eviction)"); return; }
        if (cameraDevice != null || opening) return;   // allerede aaben / aabning i gang
        opening = true;
        try {
            try { if (imageReader != null) { imageReader.close(); imageReader = null; } } catch (Throwable ignored) {}   // undgaa ImageReader-laek ved reopen
            cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String camId = pickCamera(cameraManager, Rig.useFront);
            if (camId == null) { Log.e(TAG, "intet kamera fundet"); opening = false; return; }
            targetCamId = camId;
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(camId);
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size size = pickSize(map.getOutputSizes(ImageFormat.JPEG));
            Log.i(TAG, "aabner kamera " + camId + " @ " + size.getWidth() + "x" + size.getHeight());

            imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                public void onImageAvailable(ImageReader r) { onFrame(r); }
            }, camHandler);

            cameraManager.openCamera(camId, new CameraDevice.StateCallback() {
                public void onOpened(CameraDevice device) { opening = false; cameraDevice = device; startFg(true); createSession(); }   // eleveR til camera-FGS (A14: paakraevet mens kameraet er aabent)
                // Mistede kameraet (taget af en anden app, fx Discord) -> markér optaget + genaabn ALDRIG af os selv;
                // availability-callback'en rydder flaget naar kameraet bliver ledigt igen, og demandCheck aabner da (hvis efterspurgt).
                public void onDisconnected(CameraDevice device) { Log.w(TAG, "kamera disconnected (taget af anden app)"); opening = false; Rig.cameraRunning = false; device.close(); cameraDevice = null; othersHaveCamera = true; }
                // onError = ofte en TRANSIENT HAL-fejl (ikke en anden app). Latch derfor IKKE othersHaveCamera=true
                // (det ville holde kameraet lukket til en availability-callback der maaske aldrig fyrer for den aarsag);
                // nulstil kun state, saa demandCheck proever igen om 1s. AvailabilityCallback saetter selv flaget hvis
                // en ANDEN app reelt tager kameraet (onCameraUnavailable mens vi ikke har det).
                public void onError(CameraDevice device, int error) { Log.e(TAG, "kamera-fejl " + error); opening = false; Rig.cameraRunning = false; device.close(); cameraDevice = null; }
            }, camHandler);
        } catch (CameraAccessException e) {
            opening = false;
            Log.e(TAG, "openCamera", e);
        } catch (SecurityException e) {
            opening = false;
            Log.e(TAG, "openCamera security", e);
        } catch (Throwable t) {
            // KRITISK: enhver anden fejl (fx map==null -> NPE i getOutputSizes, IllegalArgumentException fra
            // ImageReader/getCameraCharacteristics paa en billig HAL) MAA nulstille 'opening' - ellers laases
            // flaget TRUE for altid og demandCheck (!opening) genaabner ALDRIG -> kameraet dor permanent for
            // processens levetid (/stream+/snapshot=503, motion blind). demandCheck proever igen om 1s.
            opening = false;
            Log.e(TAG, "openCamera (uventet)", t);
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
                        Log.e(TAG, "session-config fejlede - slipper kameraet (demandCheck proever igen)");
                        closeCameraDevice();
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
            if (Motion.shouldSample("camera")) {          // bevaegelses-alarm paa kamera-feed
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
        destroyed = true;            // stop demand-check
        Rig.cameraRunning = false;   // kamera stoppes -> status/flags maa vise "off" (ControlServer lever videre)
        try { if (availCb != null && cameraManager != null) cameraManager.unregisterAvailabilityCallback(availCb); } catch (Throwable ignored) {}
        try { if (captureSession != null) captureSession.close(); } catch (Throwable ignored) {}
        try { if (cameraDevice != null) cameraDevice.close(); } catch (Throwable ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch (Throwable ignored) {}
        try { if (camThread != null) camThread.quitSafely(); } catch (Throwable ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
