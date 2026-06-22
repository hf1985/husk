// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

// Simpel, ren-framework UI (ingen AndroidX, ingen lambdaer -> bygger med on-phone ecj/dx OG Gradle).
// Een skaerm: status + faa toggles + deep-links til alle noedvendige indstillinger + simpel forklaring
// med link til PC-companionen paa xplat.co. Tekst er i18n (getString -> values/ engelsk, values-da/
// dansk). DeX-toggle vises kun paa DeX-kapable enheder (appen er universel). Extra finish=true ->
// start kamera-servicen hovedloest og luk (bruges af setup.sh).
public class MainActivity extends Activity {
    static final String PREFS = "husk";
    static final String KEY_DEX = "dex_reconnect";
    static final String COMPANION_URL = "https://xplat.co/husk";
    static final String KEY_SCREEN = "screen_share";

    private TextView statusView;
    private boolean hasCamera;   // host har et kamera (ellers skjules kamera-funktioner)

    // Wireless Debugging (som scrcpy/adb-broen + companion afhaenger af) findes foerst i Android 11 (API 30).
    // Paa aeldre enheder (fx Android 9) skjules WD/dev-options/companion - kun browser-skaermdeling virker der.
    private static boolean wdCapable() { return Build.VERSION.SDK_INT >= 30; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Vaek + hold display 0 taendt naar appen kommer i forgrunden (fx fjern-self-update): paa en DeX-rig er
        // telefonskaermen ofte SLUKKET -> install-dialogen ville lande paa en moerk skaerm (usynlig + ikke tap-bar).
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Rig.dexReconnect = prefs.getBoolean(KEY_DEX, false);
        Rig.loadMotionPrefs(this);   // bevaegelses-alarm-config til UI'en
        hasCamera = getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY);

        if (hasCamera && Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ android.Manifest.permission.CAMERA }, 1);
        }

        Intent in = getIntent();
        // Hovedloest sat DeX-reconnect (office-deploy: am start ... --ez dexreconnect true).
        if (in != null && in.hasExtra("dexreconnect")) {
            Rig.dexReconnect = in.getBooleanExtra("dexreconnect", false);
            prefs.edit().putBoolean(KEY_DEX, Rig.dexReconnect).apply();
        }
        boolean headless = in != null && in.getBooleanExtra("finish", false);
        if (hasCamera) {
            if (in != null) forwardCameraExtras(in);
            startCamera();
        }
        if (headless) { finish(); return; }

        setContentView(buildUi());
    }

    // ---------------- UI ----------------

    private View buildUi() {
        final int dp = (int) getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#111418"));
        root.setPadding(20 * dp, 24 * dp, 20 * dp, 24 * dp);

        root.addView(title(getString(R.string.app_name), 26, true));
        root.addView(body(getString(R.string.tagline)));
        space(root, dp, 16);

        root.addView(title(getString(R.string.status_heading), 16, false));
        statusView = body("");
        root.addView(statusView);
        refreshStatus();
        space(root, dp, 16);

        // Opdatér: hent nyeste version fra xplat.co/husk/latest.json + installer (PackageInstaller)
        Button upd = new Button(this);
        upd.setText(getString(R.string.btn_update));
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Updater.checkAndUpdate(MainActivity.this); }
        });
        root.addView(upd);
        space(root, dp, 16);

        // Toggle: kamera-streaming (start/stop servicen) - KUN hvis enheden har et kamera
        if (hasCamera) {
            Switch cam = new Switch(this);
            cam.setText(getString(R.string.toggle_camera));
            cam.setTextColor(Color.WHITE);
            cam.setChecked(Rig.cameraRunning);
            cam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean on) {
                    if (on) startCamera(); else stopCamera();
                    refreshStatus();
                }
            });
            root.addView(cam);
        }

        // Toggle: DeX-reconnect - KUN paa DeX-kapable enheder (universel app)
        if (DeXDetector.isDeXCapable(this)) {
            Switch dex = new Switch(this);
            dex.setText(getString(R.string.toggle_dex));
            dex.setTextColor(Color.WHITE);
            dex.setChecked(Rig.dexReconnect);
            dex.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton v, boolean on) {
                    Rig.dexReconnect = on;
                    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DEX, on).apply();
                    if (on && Rig.a11y != null) {
                        final RigAccessibilityService svc = Rig.a11y;
                        new Thread(new Runnable() { public void run() { svc.ensureDexUp(); } }, "husk-dexup").start();
                    }
                }
            });
            root.addView(dex);
        }
        space(root, dp, 16);

        // Toggle: skaermdeling (PERMANENT) - se+styr skaermen i browseren over Tailscale. scrcpy-
        // erstatning til enheder UDEN Wireless Debugging. Naar TIL: a11y tapper selv "Start nu" og
        // appen gen-etablerer skaermdeling efter boot (ScreenConsentActivity). Klik i browser -> a11y-tap.
        Switch scr = new Switch(this);
        scr.setText(getString(R.string.toggle_screen));
        scr.setTextColor(Color.WHITE);
        scr.setChecked(Rig.screenRunning || getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SCREEN, false));
        scr.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SCREEN, on).apply();
                if (on) startScreenShare();
                else stopService(new Intent(MainActivity.this, ScreenService.class));
                refreshStatus();
            }
        });
        root.addView(scr);
        root.addView(body(getString(R.string.screen_hint)));
        space(root, dp, 16);

        // Toggle: bevaegelses-alarm (motion-detection) - killer-feature: gratis, privat sikkerhedskamera der
        // selv alarmerer via ntfy-push ved bevaegelse. Brugeren angiver et ntfy-emne (topic); push leveres dertil.
        root.addView(title(getString(R.string.motion_heading), 16, false));
        final android.widget.EditText topic = new android.widget.EditText(this);
        topic.setHint(getString(R.string.motion_topic_hint));
        topic.setTextColor(Color.WHITE);
        topic.setHintTextColor(Color.parseColor("#6B7480"));
        topic.setText(Rig.ntfyTopic);
        root.addView(topic);
        Switch mo = new Switch(this);
        mo.setText(getString(R.string.toggle_motion));
        mo.setTextColor(Color.WHITE);
        mo.setChecked(Rig.motionEnabled);
        mo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                Rig.motionEnabled = on;
                Rig.ntfyTopic = topic.getText().toString().trim();
                Rig.saveMotionPrefs(MainActivity.this);
                Toast.makeText(MainActivity.this, getString(on ? R.string.motion_on : R.string.motion_off), Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(mo);
        root.addView(body(getString(R.string.motion_hint)));
        space(root, dp, 16);

        // Deep-links til noedvendige indstillinger
        root.addView(title(getString(R.string.settings_heading), 16, false));
        root.addView(settingsButton(getString(R.string.btn_accessibility),
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        // Udviklerindstillinger + Wireless Debugging (til scrcpy/adb-bro) - KUN paa Android 11+ (API 30),
        // hvor Wireless Debugging findes. Paa aeldre enheder (fx Android 9) virker scrcpy ikke -> skjules.
        if (wdCapable()) {
            root.addView(body(getString(R.string.devhint)));
            Button devbtn = new Button(this);
            devbtn.setText(getString(R.string.btn_enabledev));
            devbtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { autoEnableDevOptions(); }
            });
            root.addView(devbtn);
            root.addView(settingsButton(getString(R.string.btn_aboutphone),
                    new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
            root.addView(settingsButton(getString(R.string.btn_devoptions),
                    new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        }
        root.addView(settingsButton(getString(R.string.btn_battery),
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
        root.addView(settingsButton(getString(R.string.btn_appdetails),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));
        space(root, dp, 16);

        // Companion (scrcpy-skaermspejling via Wireless Debugging) - KUN paa Android 11+; ellers er
        // browser-skaermdelingen ovenfor den rette vej (companion/scrcpy virker ikke uden WD).
        if (wdCapable()) {
            root.addView(title(getString(R.string.companion_heading), 16, false));
            root.addView(body(getString(R.string.companion_explain) + "\n" + getString(R.string.companion_url)));
            Button comp = new Button(this);
            comp.setText(getString(R.string.btn_get_companion));
            comp.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse(COMPANION_URL))); }
            });
            root.addView(comp);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        return sv;
    }

    private void refreshStatus() {
        if (statusView == null) return;
        String on = getString(R.string.status_on), off = getString(R.string.status_off);
        String lan = Net.localIp(), ts = Net.tailscaleIp();
        StringBuilder s = new StringBuilder();
        s.append(getString(R.string.status_engine)).append(": ").append(Rig.a11y != null ? on : off);
        if (hasCamera) s.append("\n").append(getString(R.string.status_camera)).append(": ").append(Rig.cameraRunning ? on : off);
        s.append("\n").append(getString(R.string.status_screen)).append(": ").append(Rig.screenRunning ? on : off);
        s.append("\n").append(getString(R.string.status_local_ip)).append(": ").append(lan != null ? lan : "-");
        s.append("\n").append(getString(R.string.status_ts_ip)).append(": ").append(ts != null ? ts : "-");
        statusView.setText(s.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();   // vis FAKTISK tilstand naar appen aabnes igen (ikke stale)
    }

    private Button settingsButton(String label, final Intent intent) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { safeStart(intent); }
        });
        return btn;
    }

    private void safeStart(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        else Toast.makeText(this, getString(R.string.setting_unavailable), Toast.LENGTH_SHORT).show();
    }

    // Aktiver Udviklerindstillinger automatisk via a11y-motoren (samme idé som WD-recovery: a11y
    // driver Settings-UI'et og tapper Build-nummer). Kraever at Husk-a11y er slaaet til.
    private void autoEnableDevOptions() {
        final RigAccessibilityService svc = Rig.a11y;
        if (svc == null) { Toast.makeText(this, getString(R.string.need_a11y), Toast.LENGTH_LONG).show(); return; }
        Toast.makeText(this, getString(R.string.dev_working), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() { public void run() {
            final boolean ok = svc.ensureDeveloperOptions(false);
            runOnUiThread(new Runnable() { public void run() {
                Toast.makeText(MainActivity.this, getString(ok ? R.string.dev_ok : R.string.dev_fail), Toast.LENGTH_LONG).show();
            } });
        } }, "husk-devopt").start();
    }

    // ---------------- kamera-service ----------------

    private void forwardCameraExtras(Intent in) {
        Intent svc = new Intent(this, CameraService.class);
        if (in.hasExtra("token")) svc.putExtra("token", in.getStringExtra("token"));
        if (in.hasExtra("rot"))   svc.putExtra("rotation", in.getIntExtra("rot", 0));
        if (in.hasExtra("flip"))  svc.putExtra("flip", in.getBooleanExtra("flip", false));
        if (in.hasExtra("front")) svc.putExtra("front", in.getBooleanExtra("front", false));
        if (in.hasExtra("fps"))   svc.putExtra("fps", in.getIntExtra("fps", 10));
        startService2(svc);
    }

    private void startCamera() { startService2(new Intent(this, CameraService.class)); }
    private void stopCamera() { stopService(new Intent(this, CameraService.class)); }

    private void startService2(Intent svc) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
    }

    // ---------------- skaermdeling (MediaProjection) ----------------

    private void startScreenShare() {
        // ScreenConsentActivity haandterer MediaProjection-samtykket (a11y tapper selv "Start nu") +
        // starter ScreenService. Samme vej som boot-auto-start, saa adfaerden er ens.
        Intent i = new Intent(this, ScreenConsentActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        Toast.makeText(this, getString(R.string.screen_on), Toast.LENGTH_LONG).show();
    }

    // ---------------- smaa view-hjaelpere ----------------

    private TextView title(String t, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private TextView body(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextColor(Color.parseColor("#B0B8C0"));
        tv.setTextSize(14);
        return tv;
    }

    private void space(LinearLayout root, int dp, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h * dp));
        root.addView(v);
    }
}
