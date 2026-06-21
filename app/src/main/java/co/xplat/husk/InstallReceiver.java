// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

// Modtager PackageInstaller-status fra Updater. Vigtigst: STATUS_PENDING_USER_ACTION -> start den
// medfoelgende intent, der er systemets install-bekraeftelse (+ "tillad ukendte apps" foerste gang).
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ctx.startActivity(confirm); } catch (Throwable ignored) {}
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(ctx, ctx.getString(R.string.update_done), Toast.LENGTH_LONG).show();
            // En selv-opdatering draeber app-processen; uden dette ville ControlServeren (8090) IKKE komme
            // op igen, og en fjernstyret enhed ville gaa moerk. Genstart kamera-servicen (= ControlServer
            // + evt. auto-skaermdeling) saa fjernadgang bevares efter opdatering uden at aabne app'en.
            try {
                Intent svc = new Intent(ctx, CameraService.class);
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc); else ctx.startService(svc);
            } catch (Throwable ignored) {}
        }
    }
}
