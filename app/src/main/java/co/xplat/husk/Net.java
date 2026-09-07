// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

// IP-hjaelpere: hvad er enhedens lokale vs Tailscale-IP, og hvilke peers maa naa serverne.
// ControlServer (8090) og AdbForward (15557) binder 0.0.0.0 - begrundelsen staar i ControlServer.start().
// Beskyttelsen er derfor kilde-IP-ACL'en (peerAllowed) + en valgfri token, IKKE bindingen.
// (Her stod indtil 1.0 at vi bandt "ALDRIG 0.0.0.0". Det var forkert, og havde vaeret det laenge.)
public final class Net {
    private Net() {}

    // Alle private IPv4-adresser paa enheden (LAN + Tailscale), undtagen loopback + link-local.
    public static List<String> serveIps() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try { if (ni.isLoopback()) continue; } catch (Throwable ignored) {}
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && isPrivate(a.getHostAddress())) {
                        String ip = a.getHostAddress();
                        if (!out.contains(ip)) out.add(ip);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    // Enhedens Tailscale-IPv4 - eller null hvis enheden ikke er paa et tailnet.
    //
    // En adresse i 100.64.0.0/10 er IKKE i sig selv bevis for Tailscale: blokken er RFC 6598 CGNAT,
    // og mobiloperatoerer deler den ud til helt almindelige abonnenter. Foer 1.0 returnerede vi den
    // foerste 100.64/10-adresse paa et vilkaarligt interface, saa paa mobildata blev TELESELSKABETS
    // adresse vist som "Tailscale IP" i /info og paa hovedskaermen. Maalt af F-Droid-testeren paa en
    // Galaxy S9 helt UDEN Tailscale installeret.
    //
    // Diskriminatoren er Tailscales IPv6-ULA-praefiks fd7a:115c:a1e0::/48: vi rapporterer kun en
    // CGNAT-adresse som Tailscales naar SAMME NetworkInterface ogsaa baerer en fd7a:-adresse.
    // Bevidst konservativ: en selvhostet Headscale med et ANDET ULA-praefiks laeses som "ingen
    // Tailscale". For en ETIKET er det den rigtige side at fejle til - vi hellere viser "-" end
    // paastaar et privat net der ikke findes.
    public static String tailscaleIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try { if (ni.isLoopback()) continue; } catch (Throwable ignored) {}
                String cgnat = null;
                boolean marker = false;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isLoopbackAddress()) continue;
                    if (a instanceof Inet4Address) {
                        if (cgnat == null && isTailscale(a.getHostAddress())) cgnat = a.getHostAddress();
                    } else if (isTailscaleUla(a)) {
                        marker = true;
                    }
                }
                if (cgnat != null && marker) return cgnat;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // Tailscales IPv6-ULA-praefiks fd7a:115c:a1e0::/48 - markoeren der skiller et rigtigt tailnet
    // fra en mobiloperatoers CGNAT. Sammenlign PRAECIS 6 bytes; /48 er 48 bits.
    static boolean isTailscaleUla(InetAddress a) {
        if (a == null) return false;
        byte[] r = a.getAddress();
        return r != null && r.length == 16
            && (r[0] & 0xff) == 0xfd && (r[1] & 0xff) == 0x7a
            && (r[2] & 0xff) == 0x11 && (r[3] & 0xff) == 0x5c
            && (r[4] & 0xff) == 0xa1 && (r[5] & 0xff) == 0xe0;
    }

    // Foerste ikke-Tailscale private adresse = enhedens LAN-IP.
    public static String localIp() {
        for (String ip : serveIps()) if (!isTailscale(ip)) return ip;
        return null;
    }

    public static boolean isTailscale(String ip) {
        int[] o = octets(ip);
        return o != null && o[0] == 100 && o[1] >= 64 && o[1] <= 127;
    }

    // Kilde-IP-ACL, DELT af ControlServer (8090) + AdbForward (15557): kun loopback + privat (RFC1918) +
    // Tailscale (CGNAT 100.64/10, IPv6-ULA fc00::/7) peers maa naa serverne. Link-local tillades IKKE
    // (en nabo paa samme L2-segment maatte ellers naa ind). Sikkert sammen med 0.0.0.0-bind, fordi det
    // tjekker PEER-adressen - en offentlig kilde (fx mobildata) afvises uanset bind.
    public static boolean peerAllowed(InetAddress a) {
        if (a == null) return false;
        if (a.isLoopbackAddress()) return true;
        byte[] raw = a.getAddress();
        if (raw.length == 4) return isPrivate(a.getHostAddress());   // IPv4: 10/172.16-31/192.168/100.64-127
        return (raw[0] & 0xfe) == 0xfc;                              // IPv6 ULA fc00::/7 (Tailscale fd7a:)
    }

    static boolean isPrivate(String ip) {
        int[] o = octets(ip);
        if (o == null) return false;
        if (o[0] == 10) return true;                          // 10.0.0.0/8
        if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) return true;  // 172.16.0.0/12
        if (o[0] == 192 && o[1] == 168) return true;          // 192.168.0.0/16
        if (o[0] == 100 && o[1] >= 64 && o[1] <= 127) return true; // Tailscale CGNAT
        return false;                                          // 169.254.x (link-local) m.fl. springes over
    }

    private static int[] octets(String ip) {
        if (ip == null) return null;
        String[] p = ip.split("\\.");
        if (p.length != 4) return null;
        try { return new int[]{ Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]) }; }
        catch (Throwable t) { return null; }
    }
}
