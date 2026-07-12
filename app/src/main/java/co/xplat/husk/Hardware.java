// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Laesning + styring af telefonens fysiske hardware - det der findes paa praktisk talt alle Android 8+
// enheder, uden root. Hver metode returnerer en streng (JSON eller OK/ERR) som ControlServer skriver.
// Dangerous/special perms (lokation, mikrofon, lysstyrke-skriv) degraderer paent hvis ikke givet.
public final class Hardware {
    private Hardware() {}

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(' ');
            else b.append(ch);
        }
        return b.toString();
    }

    // Finite-guard for JSON-float: NaN/Infinity er IKKE gyldig JSON og faar en streng JSON.parse (i /control-
    // vieweren) el. jq (i spare.sh) til at kaste paa hele svaret. En sensor/HAL kan rapportere ikke-endelige
    // vaerdier -> emit "null" i stedet.
    private static String fj(float v) { return Float.isFinite(v) ? Float.toString(v) : "null"; }

    private static boolean granted(Context c, String perm) {
        try { return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; } catch (Throwable t) { return false; }
    }

    // ---------------- Sensorer ----------------

    private static int sensorType(String name) {
        if (name == null) return -1;
        switch (name.toLowerCase()) {
            case "accelerometer": case "accel": return Sensor.TYPE_ACCELEROMETER;
            case "gyroscope": case "gyro": return Sensor.TYPE_GYROSCOPE;
            case "magnetic": case "magnetometer": case "compass": return Sensor.TYPE_MAGNETIC_FIELD;
            case "light": return Sensor.TYPE_LIGHT;
            case "proximity": return Sensor.TYPE_PROXIMITY;
            case "pressure": case "barometer": return Sensor.TYPE_PRESSURE;
            case "gravity": return Sensor.TYPE_GRAVITY;
            case "linear": case "linearaccel": return Sensor.TYPE_LINEAR_ACCELERATION;
            case "rotation": case "rotationvector": return Sensor.TYPE_ROTATION_VECTOR;
            case "temperature": case "ambienttemperature": return Sensor.TYPE_AMBIENT_TEMPERATURE;
            case "humidity": return Sensor.TYPE_RELATIVE_HUMIDITY;
            case "stepcounter": case "steps": return Sensor.TYPE_STEP_COUNTER;
            default: return -1;
        }
    }

    public static String sensorsList(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return "ERR no-sensor-service";
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Sensor s : all) {
            if (!first) b.append(",");
            first = false;
            b.append("{\"name\":\"").append(esc(s.getName())).append("\",\"type\":").append(s.getType())
             .append(",\"vendor\":\"").append(esc(s.getVendor())).append("\",\"power\":").append(fj(s.getPower()))
             .append(",\"max\":").append(fj(s.getMaximumRange())).append("}");
        }
        b.append("]");
        return b.toString();
    }

    public static String readSensor(Context c, String name) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) return "ERR no-sensor-service";
        int type = sensorType(name);
        if (type < 0) return "ERR unknown-sensor:" + name;
        Sensor s = sm.getDefaultSensor(type);
        if (s == null) return "ERR sensor-not-present:" + name;
        final float[][] box = {null};
        final CountDownLatch latch = new CountDownLatch(1);
        final SensorEventListener l = new SensorEventListener() {
            public void onSensorChanged(SensorEvent e) { if (box[0] == null) { box[0] = e.values.clone(); latch.countDown(); } }
            public void onAccuracyChanged(Sensor s2, int a) {}
        };
        sm.registerListener(l, s, SensorManager.SENSOR_DELAY_FASTEST);
        try { latch.await(1800, TimeUnit.MILLISECONDS); } catch (InterruptedException e) {}
        sm.unregisterListener(l);
        if (box[0] == null) return "ERR no-reading (sensor gav ikke en vaerdi)";
        StringBuilder vb = new StringBuilder("[");
        for (int i = 0; i < box[0].length; i++) { if (i > 0) vb.append(","); vb.append(fj(box[0][i])); }
        vb.append("]");
        return "{\"sensor\":\"" + esc(s.getName()) + "\",\"type\":" + type + ",\"values\":" + vb + "}";
    }

    // ---------------- Batteri ----------------

    public static String batteryJson(Context c) {
        Intent bi = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) return "ERR no-battery-info";
        int level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int health = bi.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int plugged = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        int temp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int volt = bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        String tech = bi.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        int pct = (level >= 0 && scale > 0) ? level * 100 / scale : -1;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        String st = status == 2 ? "charging" : status == 3 ? "discharging" : status == 4 ? "not_charging" : status == 5 ? "full" : "unknown";
        String he = health == 2 ? "good" : health == 3 ? "overheat" : health == 4 ? "dead" : health == 5 ? "over_voltage" : health == 7 ? "cold" : "unknown";
        String pl = plugged == 1 ? "ac" : plugged == 2 ? "usb" : plugged == 4 ? "wireless" : "none";
        return "{\"level\":" + pct + ",\"charging\":" + charging + ",\"status\":\"" + st + "\",\"health\":\"" + he
             + "\",\"plugged\":\"" + pl + "\",\"temperatureC\":" + (temp >= 0 ? (temp / 10.0) : -1)
             + ",\"voltageMv\":" + volt + ",\"technology\":\"" + esc(tech) + "\"}";
    }

    // ---------------- Lommelygte (torch) ----------------

    public static String torch(Context c, boolean on) {
        if (Build.VERSION.SDK_INT < 23) return "ERR torch needs Android 6+";
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(f)) { cm.setTorchMode(id, on); return "OK (" + (on ? "on" : "off") + ")"; }
            }
            return "ERR no-flash-on-device";
        } catch (Throwable t) { return "ERR " + t.getClass().getSimpleName() + " (kamera maaske i brug?)"; }
    }

    // ---------------- Vibrator ----------------

    public static String vibrate(Context c, int ms) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return "ERR no-vibrator";
        if (ms <= 0) ms = 300;
        ms = Math.min(ms, 10000);   // OEVRE loft: uden det kunne /vibrate?ms=2000000000 koere motoren i dagevis (batteri/gene)
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
            return "OK (" + ms + "ms)";
        } catch (Throwable t) { return "ERR " + t; }
    }

    // ---------------- Lydstyrke ----------------

    private static int audioStream(String name) {
        if (name == null) return AudioManager.STREAM_MUSIC;
        switch (name.toLowerCase()) {
            case "ring": return AudioManager.STREAM_RING;
            case "alarm": return AudioManager.STREAM_ALARM;
            case "notification": return AudioManager.STREAM_NOTIFICATION;
            case "system": return AudioManager.STREAM_SYSTEM;
            case "call": return AudioManager.STREAM_VOICE_CALL;
            default: return AudioManager.STREAM_MUSIC;
        }
    }

    public static String volume(Context c, String stream, Integer level) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return "ERR no-audio-service";
        if (level != null) {
            int s = audioStream(stream);
            try { am.setStreamVolume(s, level, 0); return "OK (" + (stream == null ? "media" : stream) + "=" + level + ")"; }
            catch (Throwable t) { return "ERR " + t.getClass().getSimpleName() + " (DND aktiv?)"; }
        }
        String[] names = {"media", "ring", "alarm", "notification", "system", "call"};
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < names.length; i++) {
            int s = audioStream(names[i]);
            if (i > 0) b.append(",");
            b.append("\"").append(names[i]).append("\":{\"level\":").append(am.getStreamVolume(s))
             .append(",\"max\":").append(am.getStreamMaxVolume(s)).append("}");
        }
        b.append("}");
        return b.toString();
    }

    public static String ringer(Context c, String mode) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return "ERR no-audio-service";
        if (mode != null) {
            int m = mode.equals("silent") ? AudioManager.RINGER_MODE_SILENT
                  : mode.equals("vibrate") ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL;
            try { am.setRingerMode(m); return "OK (" + mode + ")"; }
            catch (Throwable t) { return "ERR " + t.getClass().getSimpleName() + " (kraever DND-adgang for silent/vibrate)"; }
        }
        int r = am.getRingerMode();
        String s = r == AudioManager.RINGER_MODE_SILENT ? "silent" : r == AudioManager.RINGER_MODE_VIBRATE ? "vibrate" : "normal";
        return "{\"mode\":\"" + s + "\"}";
    }

    // ---------------- Lysstyrke ----------------

    public static String brightness(Context c, Integer level) {
        if (level != null) {
            if (!Settings.System.canWrite(c)) return "ERR needs WRITE_SETTINGS (giv 'Aendre systemindstillinger' til Husk)";
            try {
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(0, Math.min(255, level)));
                return "OK (" + level + "/255)";
            } catch (Throwable t) { return "ERR " + t; }
        }
        try {
            int b = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, -1);
            int mode = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
            return "{\"level\":" + b + ",\"max\":255,\"auto\":" + (mode == 1) + "}";
        } catch (Throwable t) { return "ERR " + t; }
    }

    // ---------------- Display ----------------

    @SuppressWarnings("deprecation")
    public static String displayJson(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        float refresh = 0; int rot = -1;
        try {
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            refresh = d.getRefreshRate();
            rot = d.getRotation();
        } catch (Throwable t) {}
        String ro = rot == Surface.ROTATION_0 ? "0" : rot == Surface.ROTATION_90 ? "90" : rot == Surface.ROTATION_180 ? "180" : rot == Surface.ROTATION_270 ? "270" : "?";
        return "{\"width\":" + m.widthPixels + ",\"height\":" + m.heightPixels + ",\"densityDpi\":" + m.densityDpi
             + ",\"density\":" + m.density + ",\"refreshHz\":" + refresh + ",\"rotation\":\"" + ro + "\"}";
    }

    // ---------------- Netvaerk ----------------

    public static String connectivityJson(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "ERR no-connectivity-service";
        try {
            android.net.Network n = cm.getActiveNetwork();
            if (n == null) return "{\"connected\":false,\"type\":\"none\"}";
            NetworkCapabilities cap = cm.getNetworkCapabilities(n);
            String type = "other";
            if (cap != null) {
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = "wifi";
                else if (cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) type = "cellular";
                else if (cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) type = "ethernet";
                else if (cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) type = "vpn";
            }
            boolean metered = cm.isActiveNetworkMetered();
            boolean validated = cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            return "{\"connected\":true,\"type\":\"" + type + "\",\"metered\":" + metered + ",\"validated\":" + validated + "}";
        } catch (Throwable t) { return "ERR " + t; }
    }

    // ---------------- Lokation (GPS) ----------------

    public static String locationJson(Context c) {
        if (!granted(c, "android.permission.ACCESS_FINE_LOCATION") && !granted(c, "android.permission.ACCESS_COARSE_LOCATION"))
            return "ERR location-permission-not-granted (giv Husk Lokations-tilladelse)";
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return "ERR no-location-service";
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException e) { return "ERR location-permission-not-granted"; } catch (Throwable t) { return "ERR " + t; }
        if (best == null) return "ERR no-fix (ingen kendt position; er GPS taendt?)";
        return "{\"lat\":" + best.getLatitude() + ",\"lon\":" + best.getLongitude() + ",\"accuracyM\":" + best.getAccuracy()
             + ",\"altitude\":" + best.getAltitude() + ",\"time\":" + best.getTime() + ",\"provider\":\"" + esc(best.getProvider()) + "\"}";
    }

    // ---------------- Mikrofon (niveau) ----------------

    public static String micLevel(Context c) {
        if (!granted(c, "android.permission.RECORD_AUDIO")) return "ERR record-audio-permission-not-granted (giv Husk Mikrofon-tilladelse)";
        MediaRecorder mr = null;
        File tmp = null;
        try {
            tmp = new File(c.getCacheDir(), "miclvl.tmp");
            mr = new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mr.setOutputFile(tmp.getAbsolutePath());
            mr.prepare();
            mr.start();
            mr.getMaxAmplitude();          // foerste kald nulstiller baseline
            Thread.sleep(400);
            int amp = mr.getMaxAmplitude(); // 0..32767
            return "{\"amplitude\":" + amp + ",\"max\":32767}";
        } catch (Throwable t) {
            return "ERR " + t.getClass().getSimpleName() + " (mikrofon optaget?)";
        } finally {
            try { if (mr != null) { mr.stop(); } } catch (Throwable ignored) {}
            try { if (mr != null) mr.release(); } catch (Throwable ignored) {}
            try { if (tmp != null) tmp.delete(); } catch (Throwable ignored) {}
        }
    }
}
