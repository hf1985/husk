// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Modtager Godkend/Afvis fra token-notifikationen (TokenRequests). IKKE eksporteret
// (android:exported="false"): kun appens egne PendingIntents naar hertil, saa ingen anden app og
// intet HTTP-kald kan godkende en anmodning uden om notifikationen.
public class TokenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null) return;
        String a = i.getAction();
        boolean approve = TokenRequests.ACTION_APPROVE.equals(a);
        if (!approve && !TokenRequests.ACTION_DENY.equals(a)) return;
        TokenRequests.decide(c.getApplicationContext(), i.getStringExtra(TokenRequests.EXTRA_ID), approve);
    }
}
