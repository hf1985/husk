// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import java.io.ByteArrayOutputStream;

// Minimal fragmenteret-MP4 (fMP4/CMAF) muxer for EEN H.264-video-track. Bruges til at streame MediaCodec-
// H.264 live til en browser via MSE (Media Source Extensions). MSE virker over ALMINDELIG http (modsat
// WebCodecs, der kraever en secure context og derfor ikke kan bruges over http://<tailscale>:8090).
//
// init-segment (ftyp + moov m. avcC fra SPS/PPS) sendes EEN gang; derefter et media-segment (moof + mdat)
// pr. frame. MediaCodec leverer Annex-B (start-koder); vi konverterer til laengde-praefiks (AVCC) i mdat,
// og lader SPS/PPS leve i avcC (ikke i samples). moof er altid 100 bytes for 1 sample -> data_offset = 108.
class Fmp4Muxer {
    static final int TIMESCALE = 90000;
    private final int width, height;
    private int seq = 1;                 // moof sequence_number
    private long baseMediaDecodeTime = 0; // i timescale-ticks

    Fmp4Muxer(int w, int h) { this.width = w; this.height = h; }

    // ---------------- byte/box-helpers ----------------
    private static void u32(ByteArrayOutputStream o, long v) { o.write((int)(v>>24)); o.write((int)(v>>16)); o.write((int)(v>>8)); o.write((int)v); }
    private static void u16(ByteArrayOutputStream o, int v) { o.write((v>>8)&0xff); o.write(v&0xff); }
    private static void s(ByteArrayOutputStream o, String t) { for (int i=0;i<t.length();i++) o.write(t.charAt(i)&0xff); }
    private static void bytes(ByteArrayOutputStream o, byte[] b) { o.write(b,0,b.length); }
    private static byte[] box(String type, byte[] body) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        u32(o, 8 + body.length); s(o, type); bytes(o, body);
        return o.toByteArray();
    }
    private static byte[] cat(byte[]... arr) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] a : arr) o.write(a, 0, a.length);
        return o.toByteArray();
    }

    // ---------------- Annex-B -> NAL-liste ----------------
    // Splitter en Annex-B-buffer (start-koder 00 00 01 / 00 00 00 01) i raa NAL-enheder.
    static java.util.List<byte[]> splitAnnexB(byte[] d, int off, int len) {
        java.util.List<byte[]> out = new java.util.ArrayList<byte[]>();
        int i = off, end = off + len;
        int start = -1;
        while (i + 3 <= end) {
            boolean sc3 = d[i]==0 && d[i+1]==0 && d[i+2]==1;
            boolean sc4 = i+4 <= end && d[i]==0 && d[i+1]==0 && d[i+2]==0 && d[i+3]==1;
            if (sc3 || sc4) {
                int scLen = sc4 ? 4 : 3;
                if (start >= 0) { byte[] nal = new byte[i-start]; System.arraycopy(d, start, nal, 0, i-start); out.add(nal); }
                i += scLen; start = i;
            } else i++;
        }
        if (start >= 0 && start < end) { byte[] nal = new byte[end-start]; System.arraycopy(d, start, nal, 0, end-start); out.add(nal); }
        return out;
    }

    static int nalType(byte[] nal) { return nal.length > 0 ? (nal[0] & 0x1f) : -1; }

    // ---------------- init-segment ----------------
    byte[] initSegment(byte[] sps, byte[] pps) {
        return cat(ftyp(), moov(sps, pps));
    }

    private byte[] ftyp() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        s(o, "isom"); u32(o, 1);                 // major_brand, minor_version
        s(o, "isom"); s(o, "iso6"); s(o, "avc1"); s(o, "mp41");
        return box("ftyp", o.toByteArray());
    }

    private byte[] moov(byte[] sps, byte[] pps) {
        return box("moov", cat(mvhd(), trak(sps, pps), mvex()));
    }

    private byte[] mvhd() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        u32(o, 0);                 // version/flags
        u32(o, 0); u32(o, 0);      // creation/modification time
        u32(o, TIMESCALE);         // timescale
        u32(o, 0);                 // duration (unknown for live)
        u32(o, 0x00010000);        // rate 1.0
        u16(o, 0x0100); u16(o, 0); // volume + reserved
        u32(o, 0); u32(o, 0);      // reserved
        int[] m = {0x00010000,0,0, 0,0x00010000,0, 0,0,0x40000000};
        for (int v : m) u32(o, v & 0xffffffffL);
        for (int i=0;i<6;i++) u32(o, 0);   // pre_defined
        u32(o, 2);                 // next_track_ID
        return box("mvhd", o.toByteArray());
    }

    private byte[] trak(byte[] sps, byte[] pps) { return box("trak", cat(tkhd(), mdia(sps, pps))); }

    private byte[] tkhd() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        u32(o, 0x00000007);        // version0, flags: enabled+in_movie+in_preview
        u32(o, 0); u32(o, 0);      // creation/modification
        u32(o, 1);                 // track_ID
        u32(o, 0);                 // reserved
        u32(o, 0);                 // duration
        u32(o, 0); u32(o, 0);      // reserved
        u16(o, 0); u16(o, 0);      // layer + alternate_group
        u16(o, 0); u16(o, 0);      // volume + reserved
        int[] m = {0x00010000,0,0, 0,0x00010000,0, 0,0,0x40000000};
        for (int v : m) u32(o, v & 0xffffffffL);
        u32(o, ((long)width) << 16);   // width 16.16
        u32(o, ((long)height) << 16);  // height 16.16
        return box("tkhd", o.toByteArray());
    }

    private byte[] mdia(byte[] sps, byte[] pps) { return box("mdia", cat(mdhd(), hdlr(), minf(sps, pps))); }

    private byte[] mdhd() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        u32(o, 0);
        u32(o, 0); u32(o, 0);
        u32(o, TIMESCALE);
        u32(o, 0);
        u16(o, 0x55c4);            // language 'und'
        u16(o, 0);
        return box("mdhd", o.toByteArray());
    }

    private byte[] hdlr() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        u32(o, 0);
        u32(o, 0);
        s(o, "vide");
        u32(o, 0); u32(o, 0); u32(o, 0);
        s(o, "VideoHandler"); o.write(0);
        return box("hdlr", o.toByteArray());
    }

    private byte[] minf(byte[] sps, byte[] pps) {
        byte[] vmhd = box("vmhd", new byte[]{0,0,0,1, 0,0, 0,0, 0,0, 0,0}); // flags=1, graphicsmode+opcolor
        byte[] dref = box("dref", cat(new byte[]{0,0,0,0, 0,0,0,1}, box("url ", new byte[]{0,0,0,1})));
        byte[] dinf = box("dinf", dref);
        return box("minf", cat(vmhd, dinf, stbl(sps, pps)));
    }

    private byte[] stbl(byte[] sps, byte[] pps) {
        byte[] stsd = box("stsd", cat(new byte[]{0,0,0,0, 0,0,0,1}, avc1(sps, pps)));
        byte[] stts = box("stts", new byte[]{0,0,0,0, 0,0,0,0});
        byte[] stsc = box("stsc", new byte[]{0,0,0,0, 0,0,0,0});
        byte[] stsz = box("stsz", new byte[]{0,0,0,0, 0,0,0,0, 0,0,0,0});
        byte[] stco = box("stco", new byte[]{0,0,0,0, 0,0,0,0});
        return box("stbl", cat(stsd, stts, stsc, stsz, stco));
    }

    private byte[] avc1(byte[] sps, byte[] pps) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i=0;i<6;i++) o.write(0);   // reserved
        u16(o, 1);                          // data_reference_index
        u16(o, 0); u16(o, 0);               // pre_defined + reserved
        for (int i=0;i<3;i++) u32(o, 0);    // pre_defined[3]
        u16(o, width); u16(o, height);
        u32(o, 0x00480000); u32(o, 0x00480000); // h/v resolution 72dpi
        u32(o, 0);                          // reserved
        u16(o, 1);                          // frame_count
        for (int i=0;i<32;i++) o.write(0);  // compressorname
        u16(o, 0x0018);                     // depth
        u16(o, 0xffff);                     // pre_defined
        bytes(o, avcC(sps, pps));
        return box("avc1", o.toByteArray());
    }

    private byte[] avcC(byte[] sps, byte[] pps) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(1);                 // configurationVersion
        o.write(sps[1]);            // AVCProfileIndication
        o.write(sps[2]);            // profile_compatibility
        o.write(sps[3]);            // AVCLevelIndication
        o.write(0xff);              // 6 bits reserved + lengthSizeMinusOne=3
        o.write(0xe1);              // 3 bits reserved + numOfSPS=1
        u16(o, sps.length); bytes(o, sps);
        o.write(1);                 // numOfPPS=1
        u16(o, pps.length); bytes(o, pps);
        return box("avcC", o.toByteArray());
    }

    private byte[] mvex() { return box("mvex", trex()); }

    private byte[] trex() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        u32(o, 0);
        u32(o, 1);                 // track_ID
        u32(o, 1);                 // default_sample_description_index
        u32(o, 0); u32(o, 0); u32(o, 0); // default duration/size/flags
        return box("trex", o.toByteArray());
    }

    // ---------------- media-segment (moof + mdat) ----------------
    // frameAnnexB = en hel encoder-frame i Annex-B; SPS/PPS-NAL'er strippes (de bor i avcC).
    byte[] mediaSegment(byte[] frameAnnexB, int off, int len, boolean keyframe, long durationTicks) {
        // Annex-B -> AVCC (laengde-praefiks), drop SPS(7)/PPS(8)
        ByteArrayOutputStream mdatBody = new ByteArrayOutputStream();
        for (byte[] nal : splitAnnexB(frameAnnexB, off, len)) {
            int t = nalType(nal);
            if (t == 7 || t == 8) continue;   // SPS/PPS -> i avcC, ikke i samples
            if (nal.length == 0) continue;
            ByteArrayOutputStream p = new ByteArrayOutputStream();
            u32(p, nal.length); bytes(p, nal);
            byte[] pre = p.toByteArray(); mdatBody.write(pre, 0, pre.length);
        }
        byte[] sample = mdatBody.toByteArray();
        if (sample.length == 0) return null;
        int sampleFlags = keyframe ? 0x02000000 : 0x01010000;
        byte[] moof = moof(sample.length, durationTicks, sampleFlags);
        byte[] mdat = box("mdat", sample);
        baseMediaDecodeTime += durationTicks;
        seq++;
        return cat(moof, mdat);
    }

    private byte[] moof(int sampleSize, long durationTicks, int sampleFlags) {
        byte[] mfhd = box("mfhd", cat(new byte[]{0,0,0,0}, u32b(seq)));
        // tfhd: flags 0x020000 = default-base-is-moof
        byte[] tfhd = box("tfhd", cat(new byte[]{0,0x02,0,0}, u32b(1)));
        // tfdt v1: baseMediaDecodeTime (64-bit)
        ByteArrayOutputStream tf = new ByteArrayOutputStream();
        u32(tf, 0x01000000); u32(tf, baseMediaDecodeTime >>> 32); u32(tf, baseMediaDecodeTime & 0xffffffffL);
        byte[] tfdt = box("tfdt", tf.toByteArray());
        // trun: flags 0x000701 = data-offset(0x1) + sample-duration(0x100) + sample-size(0x200) + sample-flags(0x400).
        // VIGTIGT: IKKE 0x800 (composition-time-offset) - vi skriver kun 3 felter pr. sample; 0xf01 ville love et
        // 4. felt og forskyde boksen -> strikse parsere (Chromium MSE) afviser med decode-fejl (error 3).
        ByteArrayOutputStream tr = new ByteArrayOutputStream();
        u32(tr, 0x00000701); u32(tr, 1);
        u32(tr, 108);                       // data_offset (moof altid 100 bytes for 1 sample) + 8 (mdat-header)
        u32(tr, durationTicks); u32(tr, sampleSize); u32(tr, sampleFlags & 0xffffffffL);
        byte[] trun = box("trun", tr.toByteArray());
        byte[] traf = box("traf", cat(tfhd, tfdt, trun));
        return box("moof", cat(mfhd, traf));
    }

    private static byte[] u32b(long v) { ByteArrayOutputStream o = new ByteArrayOutputStream(); u32(o, v); return o.toByteArray(); }
}
