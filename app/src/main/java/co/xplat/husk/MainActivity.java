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

    private String appVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable t) { return "?"; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Vaek + hold display 0 taendt naar appen kommer i forgrunden: paa en DeX-rig er telefonskaermen
        // ofte SLUKKET, saa alt hvad appen viser ville lande paa en moerk skaerm (usynligt + ikke tap-bart).
        // Her stod "fx fjern-self-update" indtil 1.1; den vej findes ikke mere, men flaget gaelder stadig
        // enhver gang appen bringes i forgrunden paa en rig uden et vaagent panel.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Rig.dexReconnect = prefs.getBoolean(KEY_DEX, false);
        Rig.loadMotionPrefs(this);   // bevaegelses-alarm-config til UI'en
        hasCamera = getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY);

        // Een samlet anmodning: to requestPermissions i traek viser kun den ene dialog.
        java.util.List<String> perms = new java.util.ArrayList<String>();
        if (hasCamera &&
            checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.CAMERA);
        }
        // Android 13+: uden denne tilladelse kan godkendelses-notifikationen fra /token/request ikke vises.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), 1);

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
        TextView ver = body("v" + appVersion());
        ver.setTextColor(Color.parseColor("#8a93a0"));
        root.addView(ver);
        space(root, dp, 16);

        root.addView(title(getString(R.string.status_heading), 16, false));
        statusView = body("");
        root.addView(statusView);
        refreshStatus();
        space(root, dp, 16);

        buildTokenUi(root, dp);
        space(root, dp, 16);

        // Her sad indtil 1.1 en "Opdater Husk"-knap med en kilde-note og en bekraeftelses-dialog.
        // Hele den indbyggede updater er fjernet (F-Droid-fund 3-9): en app der henter og installerer
        // sine egne opdateringer omgaar butikkens signering og review og optages ikke i hovedrepoet.
        // Opdatering sker nu gennem F-Droid-klienten eller `adb install`.

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

    // Adgangstoken (1.4): feltet er den ene brugervej til at saette tokenet (den anden er
    // /token/request med godkendelse paa telefonen). Maskeret som et kodeord; "Vis" afslører det.
    private void buildTokenUi(LinearLayout root, int dp) {
        root.addView(title(getString(R.string.token_heading), 16, false));
        final android.widget.EditText field = new android.widget.EditText(this);
        final int skjult = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
        final int synlig = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        field.setInputType(skjult);
        field.setHint(getString(R.string.token_field_hint));
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.parseColor("#6B7480"));
        field.setSingleLine(true);
        field.setText(Rig.token);
        tokenField = field;
        tokenVist = Rig.token;
        root.addView(field);

        android.widget.CheckBox vis = new android.widget.CheckBox(this);
        vis.setText(getString(R.string.token_show));
        vis.setTextColor(Color.WHITE);
        vis.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton v, boolean on) {
                field.setInputType(on ? synlig : skjult);
                field.setSelection(field.getText().length());
            }
        });
        root.addView(vis);

        LinearLayout knapper = new LinearLayout(this);
        knapper.setOrientation(LinearLayout.HORIZONTAL);
        Button gen = new Button(this);
        gen.setText(getString(R.string.token_generate));
        gen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { field.setText(Rig.generateToken()); }
        });
        Button kopi = new Button(this);
        kopi.setText(getString(R.string.token_copy));
        kopi.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { kopierToken(field.getText().toString().trim()); }
        });
        Button gem = new Button(this);
        gem.setText(getString(R.string.token_save));
        gem.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = field.getText().toString().trim();
                // Uaendret felt, men tokenet er skiftet over API'et imens: Gem ville skrive det gamle tilbage.
                if (t.equals(tokenVist) && !tokenVist.equals(Rig.token)) {
                    visToken();
                    Toast.makeText(MainActivity.this, getString(R.string.token_changed), Toast.LENGTH_LONG).show();
                    return;
                }
                boolean ok = Rig.setToken(MainActivity.this, t);
                if (ok) { tokenVist = t; TokenRequests.invalidate(MainActivity.this); }
                int msg = !ok ? R.string.token_invalid : (t.isEmpty() ? R.string.token_cleared : R.string.token_saved);
                Toast.makeText(MainActivity.this, getString(msg), Toast.LENGTH_LONG).show();
            }
        });
        knapper.addView(gen);
        knapper.addView(kopi);
        knapper.addView(gem);
        root.addView(knapper);
        root.addView(body(getString(R.string.token_hint)));
    }

    private android.widget.EditText tokenField;
    private String tokenVist = "";   // den vaerdi feltet sidst blev fyldt med fra Rig.token

    // Fyld feltet igen hvis brugeren ikke har rettet i det, men tokenet er skiftet over API'et.
    private void visToken() {
        if (tokenField == null) return;
        if (!tokenField.getText().toString().trim().equals(tokenVist)) return;
        tokenVist = Rig.token;
        tokenField.setText(Rig.token);
    }

    private void kopierToken(String t) {
        if (t.isEmpty()) { Toast.makeText(this, getString(R.string.token_empty), Toast.LENGTH_SHORT).show(); return; }
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Husk token", t);
            // Android 13+ skjuler da vaerdien i udklipsholder-forhaandsvisningen (EXTRA_IS_SENSITIVE).
            android.os.PersistableBundle x = new android.os.PersistableBundle();
            x.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(x);
            cm.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.token_copied), Toast.LENGTH_SHORT).show();
        } catch (Throwable t2) {
            Toast.makeText(this, getString(R.string.token_copy_failed), Toast.LENGTH_SHORT).show();
        }
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
        visToken();
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
