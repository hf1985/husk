// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

// Token-API'et (1.4): en klient (fx PC-webcam-appen) beder om adgangstokenet, og BRUGEREN godkender
// paa telefonen via en notifikation. Ruterne bor i ControlServer; tilstanden bor her.
//
//   /token/request?client=<navn>[&new=<forslag>]  -> {"id":"<32 hex>","expires_in":120}
//   /token/status?id=<id>                          -> pending | denied | expired | approved + token
//
// Intet token: en godkendelse SAETTER det (new= hvis gyldigt, ellers genereret). Token findes:
// en godkendelse UDLEVERER det, og new= ignoreres (et skift kraever /token/set med det gamle token).
//
// VAERN
// - Godkendelse sker KUN via notifikationens handlinger, som gaar til en IKKE-eksporteret receiver
//   (TokenReceiver). Intet HTTP-endpoint kan godkende direkte.
// - PAS PAA: paa en TOKENLOES enhed er det IKKE et vaern. /rpc (fx "global notifications" + et klik)
//   er ugatet naar intet token er sat, saa enhver peer paa det private net/Tailscale kan selv trykke
//   Godkend. Det er ikke en regression - en tokenloes enhed er aaben for hele API'et i forvejen - men
//   "godkendelse paa telefonen" beskytter foerst naar et token ER sat. Lov det aldrig foer.
// - Samme graense INDE paa telefonen: automations-RPC'en paa 127.0.0.1:8127 er uautentificeret, saa
//   en anden installeret app med INTERNET kan aabne notifikationerne og trykke Godkend, ogsaa naar et
//   token er sat. Det er den kendte tillidsgraense for 8127 (README, Security); ret den dér, ikke her.
// - Handlingerne kraever ikke oplaasning (setAuthenticationRequired) med vilje: rig-telefoner koerer
//   ofte laaste med skaermen slukket, og Godkend skal kunne trykkes af den der staar med telefonen.
// - Hoejst EEN anmodning optager pladsen ad gangen (ellers 429), og den udloeber efter 120 s.
// - Id'et er 128 bit fra SecureRandom. Tokenet udleveres praecis een gang; derefter svarer id'et expired.
// - Tokenet logges aldrig.
final class TokenRequests {
    private TokenRequests() {}

    static final long TTL_MS = 120_000L;
    static final int NOTIF_ID = 4212;
    static final String CHANNEL = "husk_token_request";
    static final String ACTION_APPROVE = "co.xplat.husk.TOKEN_APPROVE";
    static final String ACTION_DENY = "co.xplat.husk.TOKEN_DENY";
    static final String EXTRA_ID = "id";

    private static final int PENDING = 0, APPROVED = 1, DENIED = 2;

    // Den ene anmodning (null = ingen). Alt roeres under klassens laas.
    private static String id = null;
    private static String client = "";
    private static String proposed = null;
    private static long createdMs = 0;
    private static int state = PENDING;
    private static String result = null;   // tokenet der udleveres ved approved (en gang)

    static String sanitizeClient(String s) {
        if (s == null) return "unknown";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 32; i++) {
            char ch = s.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == ' ' || ch == '.' || ch == '_' || ch == '-') b.append(ch);
        }
        String r = b.toString().trim();
        return r.isEmpty() ? "unknown" : r;
    }

    private static String newId() {
        byte[] r = new byte[16];
        new java.security.SecureRandom().nextBytes(r);
        StringBuilder b = new StringBuilder(32);
        for (byte x : r) b.append(String.format("%02x", x & 0xff));
        return b.toString();
    }

    private static boolean expired(long now) { return id != null && now - createdMs > TTL_MS; }

    private static void clear(Context c) {
        id = null; client = ""; proposed = null; state = PENDING; result = null;
        cancelNotif(c);
    }

    static final String BUSY = "busy";           // -> 429
    static final String NO_NOTIF = "no-notif";   // -> 503

    // Returnerer det nye id, BUSY hvis en anden anmodning optager pladsen, eller NO_NOTIF hvis
    // notifikationen ikke kan vises (saa kan ingen godkende, og en ventende anmodning ville bare
    // blokere alle andre i 120 s).
    static synchronized String request(Context c, String rawClient, String rawNew) {
        long now = System.currentTimeMillis();
        if (expired(now)) clear(c);
        // Ogsaa en AFGJORT, uafhentet anmodning optager pladsen til den er hentet eller udloebet:
        // ellers kunne en anden peer rydde et godkendt resultat (paa en tokenloes enhed er tokenet
        // da sat uden at nogen klient faar det) eller stille en ny notifikation op under samme navn.
        if (id != null) return BUSY;
        if (!notifOk(c)) return NO_NOTIF;
        id = newId();
        client = sanitizeClient(rawClient);
        proposed = (rawNew != null && !rawNew.isEmpty() && Rig.tokenValid(rawNew)) ? rawNew : null;
        createdMs = now;
        state = PENDING;
        if (!showNotif(c, id, client, Rig.token == null || Rig.token.isEmpty())) { clear(c); return NO_NOTIF; }
        return id;
    }

    // Kaldes naar tokenet skiftes ad en ANDEN vej (feltet i appen, /token/set): et godkendt men
    // uafhentet resultat ville ellers udlevere det gamle, nu ugyldige token.
    static synchronized void invalidate(Context c) {
        if (id != null) clear(c);
    }

    private static boolean notifOk(Context c) {
        if (c == null) return false;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.areNotificationsEnabled();
        } catch (Throwable t) { return false; }
    }

    // JSON til /token/status.
    static synchronized String status(Context c, String reqId) {
        long now = System.currentTimeMillis();
        if (expired(now)) clear(c);
        if (id == null || reqId == null || !java.security.MessageDigest.isEqual(id.getBytes(), reqId.getBytes())) {
            return "{\"status\":\"expired\"}";
        }
        if (state == PENDING) return "{\"status\":\"pending\"}";
        if (state == DENIED) { clear(c); return "{\"status\":\"denied\"}"; }
        String t = result;
        clear(c);   // udleveret: id'et svarer expired herefter
        return "{\"status\":\"approved\",\"token\":\"" + t + "\"}";
    }

    // Kaldes KUN fra TokenReceiver (notifikationens handlinger).
    static synchronized void decide(Context c, String reqId, boolean approve) {
        long now = System.currentTimeMillis();
        if (expired(now)) { clear(c); return; }
        if (id == null || reqId == null || !id.equals(reqId) || state != PENDING) { cancelNotif(c); return; }
        cancelNotif(c);
        if (!approve) { state = DENIED; return; }
        String cur = Rig.token;
        if (cur == null || cur.isEmpty()) {
            String t = proposed != null ? proposed : Rig.generateToken();
            if (!Rig.setToken(c, t)) { state = DENIED; return; }
            cur = t;
        }
        result = cur;
        state = APPROVED;
    }

    private static boolean showNotif(Context c, String reqId, String who, boolean saetter) {
        if (c == null) return false;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.token_notif_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
            NotificationChannel faktisk = nm.getNotificationChannel(CHANNEL);   // brugeren kan have slaaet den fra
            if (faktisk != null && faktisk.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
            int kode = ++pendingSeq * 2;   // unik pr. anmodning: en gammel notifikations handling kan ikke
                                           // omskrives til at godkende en nyere (FLAG_UPDATE_CURRENT-faelden)
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setContentTitle(c.getString(R.string.token_notif_title, who))
                    .setContentText(c.getString(saetter ? R.string.token_notif_text_set : R.string.token_notif_text_get))
                    .setStyle(new Notification.BigTextStyle().bigText(
                            c.getString(saetter ? R.string.token_notif_text_set : R.string.token_notif_text_get)))
                    .setAutoCancel(true)
                    .setTimeoutAfter(TTL_MS)
                    .addAction(new Notification.Action.Builder(null, c.getString(R.string.token_approve),
                            pending(c, ACTION_APPROVE, reqId, kode)).build())
                    .addAction(new Notification.Action.Builder(null, c.getString(R.string.token_deny),
                            pending(c, ACTION_DENY, reqId, kode + 1)).build())
                    .build();
            nm.notify(NOTIF_ID, n);
            return true;
        } catch (Throwable t) { return false; }
    }

    private static int pendingSeq = 0;

    private static PendingIntent pending(Context c, String action, String reqId, int code) {
        Intent i = new Intent(c, TokenReceiver.class).setAction(action).putExtra(EXTRA_ID, reqId);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelNotif(Context c) {
        try {
            if (c != null) ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIF_ID);
        } catch (Throwable ignored) {}
    }
}
