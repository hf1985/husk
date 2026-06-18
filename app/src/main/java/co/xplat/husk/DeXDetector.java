// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.content.Context;
import android.content.res.Configuration;

// Detektér om enheden er Samsung DeX-kapabel / om DeX er aktiv - UDEN Samsung-proprietaer SDK.
// Bruges til at vise DeX-reconnect-toggle KUN paa DeX-kapable enheder (appen er universel og skal
// vaere ren paa ikke-DeX-telefoner). Lagdelt heuristik (mest robust foerst):
//   A) AOSP Configuration.UI_MODE_TYPE_DESK (desk/desktop-mode, API 17+)
//   B) Samsung Configuration.semDesktopModeEnabled (reflection, One UI < 8)
//   C) "desktopmode" system-service (reflection, One UI 8+)
// Alle reflection-stier fejler stille -> no-op paa ikke-Samsung-enheder.
public final class DeXDetector {
    private DeXDetector() {}

    // Er DeX-tilstand aktiv lige nu?
    public static boolean isDeXActive(Context ctx) {
        Configuration cfg = ctx.getResources().getConfiguration();
        if ((cfg.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK) return true;

        // B) Samsung semDesktopModeEnabled (One UI < 8)
        try {
            Class<?> c = cfg.getClass();
            int enabledConst = c.getField("SEM_DESKTOP_MODE_ENABLED").getInt(c);
            int current = c.getField("semDesktopModeEnabled").getInt(cfg);
            if (current == enabledConst) return true;
        } catch (Throwable ignored) {}

        // C) desktopmode-service (One UI 8+)
        try {
            Object svc = ctx.getSystemService("desktopmode");
            if (svc != null) {
                Object state = svc.getClass().getMethod("getDesktopModeState").invoke(svc);
                Object enabled = state.getClass().getMethod("getEnabled").invoke(state);
                if (enabled instanceof Boolean && (Boolean) enabled) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    // Er enheden DeX-KAPABEL (vis toggle)? DeX behoever ikke vaere aktiv lige nu. Heuristik:
    // Samsung-enhed med kendt DeX-understoettelse, ELLER DeX er aktiv lige nu, ELLER en af
    // Samsung-reflection-felterne overhovedet findes (= Samsung-framework med DeX-API).
    public static boolean isDeXCapable(Context ctx) {
        if (isDeXActive(ctx)) return true;
        // Samsung-framework? semDesktopModeEnabled-feltet eksisterer kun paa Samsung-builds med DeX.
        try {
            ctx.getResources().getConfiguration().getClass().getField("semDesktopModeEnabled");
            return true;
        } catch (Throwable ignored) {}
        // desktopmode-service tilstede (nyere Samsung)?
        try {
            if (ctx.getSystemService("desktopmode") != null) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
