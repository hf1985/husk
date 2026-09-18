// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

// M6: start kamera-servicen igen efter reboot (BOOT_COMPLETED). Den aktiverede a11y-service
// re-binder Android selv ved boot (jf. DexRPC), saa WD-recovery er klar; her sikrer vi blot at
// capture+server kommer op uden manuel indgriben. Ingen Termux/crontab noedvendig (constraint).
//
// J4 (v0.9.25): haandter OGSAA MY_PACKAGE_REPLACED. Enhver opdatering af appen draeber
// app-processen; paa Android 12+ er en FGS-genstart fra en almindelig baggrunds-kontekst blokeret af
// baggrunds-FGS-restriktionen, saa 8090/ControlServer laa nede indtil et rigtigt boot (eller en
// manuel QUICKBOOT-nudge). MY_PACKAGE_REPLACED leveres KUN til den netop-opdaterede app og staar paa
// Androids FGS-start-undtagelsesliste (samme klasse som BOOT_COMPLETED), saa FGS-starten herfra ER
// tilladt -> ControlServeren rejser sig selv efter enhver opdatering.
//
// Grenen er IKKE blevet doed med 1.1's fjernelse af den indbyggede updater: den daekker nu
// F-Droid-klientens opdatering og `adb install -r`, som ogsaa udloeser MY_PACKAGE_REPLACED.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent != null ? intent.getAction() : null;
        if (a == null) return;
        if (a.equals(Intent.ACTION_BOOT_COMPLETED) || a.equals("android.intent.action.QUICKBOOT_POWERON")
                || a.equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
            Log.i("Husk", a + " -> starter CameraService");
            Intent svc = new Intent(context, CameraService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        }
    }
}
