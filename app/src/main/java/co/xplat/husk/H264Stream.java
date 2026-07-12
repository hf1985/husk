// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// Hardware-accelereret skaerm-stream: MediaProjection -> VirtualDisplay -> MediaCodec H.264 (surface-input =
// hardware-encode, ingen kopi) -> fMP4 (Fmp4Muxer) -> live til browser via MSE. Langt mindre baandbredde +
// lavere lag end MJPEG (inter-frame-kompression). Opt-in: startes foerst naar en klient henter /screen.mp4;
// MJPEG-/control roeres ikke. Bruger den projection ScreenService allerede har (skaermdeling skal vaere paa).
class H264Stream {
    static final String TAG = "Husk";

    static class Client { OutputStream out; boolean started = false; }

    private final MediaProjection projection;
    private final int w, h, dpi;
    private MediaCodec codec;
    private VirtualDisplay vdisplay;
    private Surface inputSurface;
    private Thread drainThread;
    private volatile boolean running = false;
    private Fmp4Muxer muxer;
    private volatile byte[] initSeg = null;
    private volatile String codecStr = null;   // "avc1.PPCCLL" til MSE addSourceBuffer (matcher SPS-profil/level)
    private byte[] sps = null, pps = null;
    private long prevPtsUs = -1;
    private final List<Client> clients = new ArrayList<Client>();

    H264Stream(MediaProjection p, int w, int h, int dpi) { this.projection = p; this.w = w; this.h = h; this.dpi = dpi; }

    void start() throws Exception {
        muxer = new Fmp4Muxer(w, h);
        MediaFormat fmt = MediaFormat.createVideoFormat("video/avc", w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(800000, w * h * 4));
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        fmt.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);   // keyframe ~hvert sekund (klient-sync + recovery)
        fmt.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000L);   // gentag sidste frame v. stilstand (1s) -> /controlhw staar ikke tom
        // Lav-lag-encoding: realtid-prioritet + (API30+) eksplicit lav-latens. CBR/bitrate-mode er BEVIDST udeladt:
        // det fik den emulerede software-encoder til at stoppe frame-leveringen efter ~5s -> buffer-underrun/stall.
        try { fmt.setInteger("priority", 0); } catch (Throwable ignored) {}   // 0 = realtime
        if (Build.VERSION.SDK_INT >= 30) { try { fmt.setInteger(MediaFormat.KEY_LATENCY, 1); } catch (Throwable ignored) {} }
        codec = MediaCodec.createEncoderByType("video/avc");
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = codec.createInputSurface();
        codec.start();
        vdisplay = projection.createVirtualDisplay("husk-h264", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, null);
        running = true;
        drainThread = new Thread(new Runnable() { public void run() { drain(); } }, "h264-drain");
        drainThread.setDaemon(true);
        drainThread.start();
        Log.i(TAG, "h264: encoder " + w + "x" + h + " startet");
    }

    synchronized Client addClient(OutputStream out) {
        Client c = new Client(); c.out = out; clients.add(c);
        requestKeyframe();   // saa den nye klient hurtigt rammer et keyframe og kan synkronisere
        return c;
    }
    synchronized boolean hasClient(Client c) { return clients.contains(c); }
    synchronized void removeClient(Client c) { clients.remove(c); }
    synchronized int clientCount() { return clients.size(); }

    private void requestKeyframe() {
        try {
            if (codec != null) { Bundle b = new Bundle(); b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0); codec.setParameters(b); }
        } catch (Throwable ignored) {}
    }

    private void drain() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running) {
            try {
                int idx = codec.dequeueOutputBuffer(info, 50000);
                if (idx < 0) continue;
                ByteBuffer buf = codec.getOutputBuffer(idx);
                if (buf != null && info.size > 0) {
                    buf.position(info.offset); buf.limit(info.offset + info.size);
                    byte[] data = new byte[info.size]; buf.get(data);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        extractSpsPps(data);
                    } else {
                        boolean key = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                        long dur = 3000;   // ~1/30 s i 90k som fallback
                        if (prevPtsUs >= 0) dur = Math.max(1, (info.presentationTimeUs - prevPtsUs) * 90000L / 1000000L);
                        prevPtsUs = info.presentationTimeUs;
                        byte[] frag = muxer.mediaSegment(data, 0, data.length, key, dur);
                        if (frag != null) publish(frag, key);
                    }
                }
                codec.releaseOutputBuffer(idx, false);
            } catch (Throwable t) { if (running) Log.e(TAG, "h264 drain", t); break; }
        }
    }

    String getCodec() { return codecStr; }

    private static String h2(byte b) { String s = Integer.toHexString(b & 0xff); return s.length() == 1 ? "0" + s : s; }

    private void extractSpsPps(byte[] csd) {
        for (byte[] nal : Fmp4Muxer.splitAnnexB(csd, 0, csd.length)) {
            int t = Fmp4Muxer.nalType(nal);
            if (t == 7) sps = nal; else if (t == 8) pps = nal;
        }
        if (sps != null && pps != null && sps.length >= 4) {
            initSeg = muxer.initSegment(sps, pps);
            codecStr = "avc1." + h2(sps[1]) + h2(sps[2]) + h2(sps[3]);   // profile_idc, constraints, level_idc
        }
    }

    private void publish(byte[] frag, boolean key) {
        // KRITISK: hold IKKE 'this'-monitoren under de BLOKERENDE socket-writes. Foer gjorde publish() det,
        // saa en enkelt hang'ende /screen.mp4-klient (pauset fane / net-stall) frøs hele H.264-subsystemet OG
        // ANR'ede main-traaden naar ScreenService.onDestroy -> Rig.stopH264() ville tage samme laas. Nu tages
        // et snapshot af klientlisten under laas, writes sker UDEN laas, og fejlede klienter fjernes bagefter.
        // (Rest: en stall'et klient sinker stadig drain-loopen til dens socket lukkes af dens egen servlet-loop;
        // MJPEG-/control er upaavirket, og der er ingen ANR laengere. Fuld per-klient-backpressure = TODO.)
        byte[] init;
        java.util.List<Client> snapshot;
        synchronized (this) {
            if (initSeg == null) return;
            init = initSeg;
            snapshot = new ArrayList<Client>(clients);
        }
        java.util.List<Client> failed = null;
        for (Client c : snapshot) {
            try {
                if (!c.started) {
                    if (!key) continue;             // ny klient venter paa keyframe foer init+frame sendes
                    c.out.write(init); c.started = true;
                }
                c.out.write(frag); c.out.flush();
            } catch (Throwable t) {
                if (failed == null) failed = new ArrayList<Client>();
                failed.add(c);                       // klient lukkede -> fjern (efter loopet, under laas)
            }
        }
        if (failed != null) synchronized (this) { clients.removeAll(failed); }
    }

    void stop() {
        running = false;
        try { if (drainThread != null) drainThread.interrupt(); } catch (Throwable ignored) {}
        try { if (vdisplay != null) vdisplay.release(); } catch (Throwable ignored) {}
        try { if (codec != null) { codec.stop(); codec.release(); } } catch (Throwable ignored) {}
        try { if (inputSurface != null) inputSurface.release(); } catch (Throwable ignored) {}
        synchronized (this) { clients.clear(); }
    }
}
