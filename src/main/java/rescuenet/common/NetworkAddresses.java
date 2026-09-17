package rescuenet.common;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;

public final class NetworkAddresses {
    private NetworkAddresses() {
    }

    public static String localHostName() {
        try {
            InetAddress lan = firstLanIpv4();
            if (lan != null) {
                return lan.getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    public static String rmiUrl(String host, int port, String name) {
        return "rmi://" + host + ":" + port + "/" + name;
    }

    private static InetAddress firstLanIpv4() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address ipv4 && !ipv4.isLoopbackAddress()) {
                        return ipv4;
                    }
                }
            }
        } catch (SocketException ignored) {
            return null;
        }
        return null;
    }
}
