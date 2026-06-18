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
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent != null ? intent.getAction() : null;
        if (a == null) return;
        if (a.equals(Intent.ACTION_BOOT_COMPLETED) || a.equals("android.intent.action.QUICKBOOT_POWERON")) {
            Log.i("Husk", "BOOT_COMPLETED -> starter CameraService");
            Intent svc = new Intent(context, CameraService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        }
    }
}
