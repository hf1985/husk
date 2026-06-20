// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

// Usynlig (translucent) aktivitet der etablerer skaermdeling. MediaProjection KRAEVER en aktivitet +
// et samtykke pr. session (Android-sikkerhed); det kan ikke gives rent programmatisk. For at goere
// skaermdeling PERMANENT (hands-free efter boot) lader vi a11y-motoren selv tappe "Start nu" i
// samtykke-dialogen. Bruges baade af UI-toggle og af CameraService.maybeAutoScreenShare() ved boot.
public class ScreenConsentActivity extends Activity {
    static final int REQ = 7;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ);
            // Hands-free: a11y tapper selv "Start nu" (+ "Vis ikke igen" hvis den findes) i dialogen.
            final RigAccessibilityService svc = Rig.a11y;
            if (svc != null) new Thread(new Runnable() { public void run() { svc.acceptScreenConsent(); } }, "husk-screenconsent").start();
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ && res == RESULT_OK && data != null) {
            Intent svc = new Intent(this, ScreenService.class);
            svc.putExtra("resultCode", res);
            svc.putExtra("data", data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
            // Husk at brugeren vil have skaermdeling -> gen-etabl. automatisk efter boot.
            getSharedPreferences("husk", Context.MODE_PRIVATE).edit().putBoolean("screen_share", true).apply();
        }
        finish();
    }
}
