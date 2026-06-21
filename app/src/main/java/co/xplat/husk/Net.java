// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 xplat <https://xplat.co>

package co.xplat.husk;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

// IP-hjaelpere: hvilke adresser skal HTTP-serveren lytte paa, og hvad er enhedens lokale vs Tailscale-IP.
// Husk virker baade paa rent LAN (uden Tailscale) OG over Tailscale - vi binder begge private adresser
// (men ALDRIG 0.0.0.0). Token + privat-net er beskyttelsen; jf. ControlServer.
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

    // Tailscale CGNAT 100.64.0.0/10 (100.64.x - 100.127.x).
    public static String tailscaleIp() {
        for (String ip : serveIps()) if (isTailscale(ip)) return ip;
        return null;
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

    // Kilde-IP-ACL, DELT af ControlServer (8090) + AdbForward (5557): kun loopback + privat (RFC1918) +
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
