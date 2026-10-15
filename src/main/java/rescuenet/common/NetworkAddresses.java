package rescuenet.common;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NetworkAddresses {
    private static final Pattern DEFAULT_ROUTE = Pattern.compile(
            "^\\s*0\\.0\\.0\\.0\\s+0\\.0\\.0\\.0\\s+(\\S+)\\s+(\\S+)\\s+(\\d+)\\s*$"
    );
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(?:\\.\\d{1,3}){3}");

    private NetworkAddresses() {
    }

    public static String localHostName() {
        try {
            InetAddress viaRoute = ipv4OnDefaultRoute();
            if (viaRoute != null) {
                return viaRoute.getHostAddress();
            }
            InetAddress lan = firstLanIpv4();
            if (lan != null) {
                return lan.getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    public static String announcedHost() {
        String property = System.getProperty("java.rmi.server.hostname");
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        return localHostName();
    }

    public static String rmiUrl(String host, int port, String name) {
        return "rmi://" + host + ":" + port + "/" + name;
    }

    static boolean looksVirtual(String name) {
        String haystack = name.toLowerCase(Locale.ROOT);
        return containsAny(
                haystack,
                "vmware",
                "vmnet",
                "virtualbox",
                "vbox",
                "hyper-v",
                "vethernet",
                "docker",
                "wsl",
                "bluetooth",
                "loopback",
                "pseudo",
                "tap-",
                "tun0",
                "tun1"
        );
    }

    static boolean looksPreferred(String name) {
        String haystack = name.toLowerCase(Locale.ROOT);
        return containsAny(haystack, "wi-fi", "wifi", "wlan", "wireless");
    }

    static List<String> defaultRouteInterfaceIps(String routePrint) {
        List<String[]> rows = new ArrayList<>();
        for (String line : routePrint.split("\\R")) {
            Matcher matcher = DEFAULT_ROUTE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String gateway = matcher.group(1);
            String iface = matcher.group(2);
            int metric = Integer.parseInt(matcher.group(3));
            if (!IPV4.matcher(iface).matches()) {
                continue;
            }
            if (!IPV4.matcher(gateway).matches() || "0.0.0.0".equals(gateway)) {
                continue;
            }
            rows.add(new String[]{iface, Integer.toString(metric)});
        }
        rows.sort(Comparator.comparingInt(row -> Integer.parseInt(row[1])));
        List<String> ips = new ArrayList<>();
        for (String[] row : rows) {
            if (!ips.contains(row[0])) {
                ips.add(row[0]);
            }
        }
        return ips;
    }

    private static InetAddress ipv4OnDefaultRoute() {
        try {
            Process process = new ProcessBuilder("route", "print", "-4")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes());
            for (String ip : defaultRouteInterfaceIps(output)) {
                InetAddress match = usableAddress(ip);
                if (match != null) {
                    return match;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static InetAddress usableAddress(String ip) throws SocketException {
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (skipInterface(nic)) {
                continue;
            }
            InetAddress ipv4 = firstIpv4(nic);
            if (ipv4 != null && ip.equals(ipv4.getHostAddress())) {
                return ipv4;
            }
        }
        return null;
    }

    private static InetAddress firstLanIpv4() {
        List<InetAddress> preferred = new ArrayList<>();
        List<InetAddress> fallback = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (skipInterface(nic)) {
                    continue;
                }
                InetAddress ipv4 = firstIpv4(nic);
                if (ipv4 == null) {
                    continue;
                }
                String label = nic.getName() + " " + String.valueOf(nic.getDisplayName());
                if (looksPreferred(label)) {
                    preferred.add(ipv4);
                } else {
                    fallback.add(ipv4);
                }
            }
        } catch (SocketException ignored) {
            return null;
        }
        if (!preferred.isEmpty()) {
            return preferred.getFirst();
        }
        if (!fallback.isEmpty()) {
            return fallback.getFirst();
        }
        return null;
    }

    private static boolean skipInterface(NetworkInterface nic) throws SocketException {
        if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint()) {
            return true;
        }
        String label = nic.getName() + " " + String.valueOf(nic.getDisplayName());
        return looksVirtual(label);
    }

    private static InetAddress firstIpv4(NetworkInterface nic) {
        for (InetAddress address : Collections.list(nic.getInetAddresses())) {
            if (address instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress()) {
                return ipv4;
            }
        }
        return null;
    }

    private static boolean containsAny(String haystack, String... tokens) {
        for (String token : tokens) {
            if (haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
