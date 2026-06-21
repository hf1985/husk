// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.graphics.Bitmap;

import java.util.HashMap;

// Bevægelsesdetektion: gør enhver gammel telefon til et gratis, privat overvågningskamera der SELV
// holder øje og alarmerer. Sammenligner på hinanden følgende frames (nedskaleret til et lille gråtone-
// gitter, blok-diff mod en tærskel). Ved nok ændring + efter en cooldown udløses en hændelse, der
// logges (Rig.motionEvents -> /events) og sendes som push via ntfy (Ntfy). Kører LET: kun ~2 samples/sek
// og kun et 32x32-gitter, så batteriet holder. Ren framework (ingen deps), minSdk 26.
public final class Motion {
    private Motion() {}

    static final int GRID = 32;          // nedskalér hver frame til 32x32 gråtone (billigt)
    static final long SAMPLE_MS = 500;   // analysér ~2 frames/sek uanset kildens fps

    private static final HashMap<String, int[]> prev = new HashMap<String, int[]>();
    private static final HashMap<String, Long> lastTrigger = new HashMap<String, Long>();
    private static volatile long lastSample = 0;

    // Hurtig gate så kalderen kan springe dyr JPEG-afkodning over når der ikke skal samples.
    public static boolean shouldSample() {
        return Rig.motionEnabled && (System.currentTimeMillis() - lastSample) >= SAMPLE_MS;
    }

    // Kald fra onFrame med den seneste frame som Bitmap. source = "camera" eller "screen" (egen
    // historik pr. kilde, så kamera- og skærm-feed ikke forveksles). Synchronized = serialiseret.
    public static synchronized void feed(Bitmap bmp, String source) {
        if (!Rig.motionEnabled || bmp == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSample < SAMPLE_MS) return;
        lastSample = now;

        int[] g = downGray(bmp);
        int[] p = prev.get(source);
        prev.put(source, g);
        if (p == null || p.length != g.length) return;   // første frame: kun baseline

        int sens = Math.max(1, Math.min(10, Rig.motionSensitivity));
        int pixThresh = 14 + (10 - sens) * 6;            // hvor meget en celle skal ændre sig (lav sens = højere tærskel)
        int changed = 0;
        for (int i = 0; i < g.length; i++) if (Math.abs(g[i] - p[i]) > pixThresh) changed++;
        int pct = changed * 100 / g.length;
        int trigPct = 3 + (10 - sens) * 2;               // hvor mange % celler der skal ændre sig for at udløse

        Long lt = lastTrigger.get(source);
        if (pct >= trigPct && (lt == null || now - lt > Rig.motionCooldownMs)) {
            lastTrigger.put(source, now);
            Rig.addMotionEvent(now, source, pct);
            Ntfy.notifyMotion(source, pct);
        }
    }

    private static int[] downGray(Bitmap bmp) {
        Bitmap s = Bitmap.createScaledBitmap(bmp, GRID, GRID, true);
        int[] px = new int[GRID * GRID];
        try { s.getPixels(px, 0, GRID, 0, 0, GRID, GRID); }
        finally { if (s != bmp) s.recycle(); }
        int[] g = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            g[i] = (((c >> 16) & 0xff) * 30 + ((c >> 8) & 0xff) * 59 + (c & 0xff) * 11) / 100;  // luma
        }
        return g;
    }
}
