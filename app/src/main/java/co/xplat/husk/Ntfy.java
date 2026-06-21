// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Push-besked ved bevægelse via ntfy (gratis, FOSS, ingen konto) - sender til en bruger-valgt topic på
// en konfigurerbar server (default ntfy.sh, der bruger Let's Encrypt/ISRG -> betroet helt ned til Android 7).
// Bruger ntfy's JSON-publish (POST {topic,title,message,...} til server-roden), så dansk æ/ø/å går korrekt
// igennem som UTF-8 (i stedet for via HTTP-headere der ikke er UTF-8-sikre). Vedhæfter et live-snapshot via
// "attach" = enhedens egen Tailscale-snapshot-URL, så man kan se HVAD der bevægede sig direkte fra beskeden.
public final class Ntfy {
    private Ntfy() {}

    public static void notifyMotion(final String source, final int pct) {
        final String topic = Rig.ntfyTopic;
        if (topic == null || topic.trim().isEmpty()) return;   // ingen topic sat -> kun /events-loggen, ingen push
        new Thread(new Runnable() { public void run() {
            HttpURLConnection c = null;
            try {
                String base = Rig.ntfyServer;
                if (base == null || base.trim().isEmpty()) base = "https://ntfy.sh";
                base = base.trim().replaceAll("/+$", "");

                JSONObject j = new JSONObject();
                j.put("topic", topic.trim());
                j.put("title", "Husk: bevægelse registreret");
                j.put("message", ("camera".equals(source) ? "Kameraet" : "Skærmen")
                        + " registrerede bevægelse (" + pct + "% ændring)");
                j.put("priority", 4);
                JSONArray tags = new JSONArray(); tags.put("eyes"); j.put("tags", tags);
                String ts = Net.tailscaleIp();
                if (ts != null) {
                    // Live-snapshot fra enheden (nås af brugerens andre Tailscale-enheder). Ingen token i URL'en:
                    // /snapshot er gated af kilde-IP-ACL'en; sættes der OGSÅ en token, leveres beskeden stadig,
                    // kun forhåndsvisningen kræver da token.
                    j.put("attach", "http://" + ts + ":8090/" + ("screen".equals(source) ? "screen.jpg" : "snapshot"));
                }
                byte[] body = j.toString().getBytes("UTF-8");

                c = (HttpURLConnection) new URL(base).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", "Husk");
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                OutputStream o = c.getOutputStream();
                o.write(body);
                o.close();
                int code = c.getResponseCode();
                Rig.lastNtfy = "ntfy " + code + " -> " + topic.trim();
            } catch (Throwable t) {
                Rig.lastNtfy = "ntfy ERR " + t.getClass().getSimpleName() + ": " + t.getMessage();
            } finally {
                if (c != null) try { c.disconnect(); } catch (Throwable ignored) {}
            }
        } }, "husk-ntfy").start();
    }
}
