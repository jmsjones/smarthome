/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.core.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some utility functions related to network interfaces etc.
 *
 * @author Markus Rathgeb - Initial contribution and API
 * @author Mark Herwege - Added methods to find broadcast address(es)
 * @author Stefan Triller - Converted to OSGi service with primary ipv4 conf
 */
@Component(configurationPid = "org.eclipse.smarthome.network", property = { "service.pid=org.eclipse.smarthome.network",
        "service.config.description.uri=system:network", "service.config.label=Network Settings",
        "service.config.category=system" })
@NonNullByDefault
public class NetUtil implements NetworkAddressService {

    private static final String PRIMARY_ADDRESS = "primaryAddress";
    private static final String BROADCAST_ADDRESS = "broadcastAddress";
    private static final Logger LOGGER = LoggerFactory.getLogger(NetUtil.class);

    private static final Pattern IPV4_PATTERN = Pattern
            .compile("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    private @Nullable String primaryAddress;
    private @Nullable String configuredBroadcastAddress;

    @Activate
    protected void activate(Map<String, Object> props) {
        modified(props);
    }

    @Modified
    public synchronized void modified(Map<String, Object> config) {
        String primaryAddressConf = (String) config.get(PRIMARY_ADDRESS);
        if (primaryAddressConf == null || primaryAddressConf.isEmpty() || !isValidIPConfig(primaryAddressConf)) {
            // if none is specified we return the default one for backward compatibility
            primaryAddress = getFirstLocalIPv4Address();
        } else {
            primaryAddress = primaryAddressConf;
        }

        String broadcastAddressConf = (String) config.get(BROADCAST_ADDRESS);
        if (broadcastAddressConf == null || broadcastAddressConf.isEmpty() || !isValidIPConfig(broadcastAddressConf)) {
            // if none is specified we return the one matching the primary ip
            configuredBroadcastAddress = getPrimaryBroadcastAddress();
        } else {
            configuredBroadcastAddress = broadcastAddressConf;
        }
    }

    @Override
    public @Nullable String getPrimaryIpv4HostAddress() {
        String primaryIP;

        if (primaryAddress != null) {
            String[] addrString = primaryAddress.split("/");
            if (addrString.length > 1) {
                String ip = getIPv4inSubnet(addrString[0], addrString[1]);
                if (ip == null) {
                    // an error has occurred, using first interface like nothing has been configured
                    LOGGER.warn("Invalid address '{}', will use first interface instead.", primaryAddress);
                    primaryIP = getFirstLocalIPv4Address();
                } else {
                    primaryIP = ip;
                }
            } else {
                primaryIP = addrString[0];
            }
        } else {
            // we do not seem to have any network interfaces
            primaryIP = null;
        }
        return primaryIP;
    }

    /**
     * @deprecated Please use the NetworkAddressService with {@link #getPrimaryIpv4HostAddress()}
     *
     *             Get the first candidate for a local IPv4 host address (non loopback, non localhost).
     */
    @Deprecated
    public static @Nullable String getLocalIpv4HostAddress() {
        try {
            String hostAddress = null;
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface current = interfaces.nextElement();
                if (!current.isUp() || current.isLoopback() || current.isVirtual() || current.isPointToPoint()) {
                    continue;
                }
                final Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    final InetAddress currentAddr = addresses.nextElement();
                    if (currentAddr.isLoopbackAddress() || (currentAddr instanceof Inet6Address)) {
                        continue;
                    }
                    if (hostAddress != null) {
                        LOGGER.warn("Found multiple local interfaces - ignoring {}", currentAddr.getHostAddress());
                    } else {
                        hostAddress = currentAddr.getHostAddress();
                    }
                }
            }
            return hostAddress;
        } catch (SocketException ex) {
            LOGGER.error("Could not retrieve network interface: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private @Nullable String getFirstLocalIPv4Address() {
        try {
            String hostAddress = null;
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface current = interfaces.nextElement();
                if (!current.isUp() || current.isLoopback() || current.isVirtual() || current.isPointToPoint()) {
                    continue;
                }
                final Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    final InetAddress currentAddr = addresses.nextElement();
                    if (currentAddr.isLoopbackAddress() || (currentAddr instanceof Inet6Address)) {
                        continue;
                    }
                    if (hostAddress != null) {
                        LOGGER.warn("Found multiple local interfaces - ignoring {}", currentAddr.getHostAddress());
                    } else {
                        hostAddress = currentAddr.getHostAddress();
                    }
                }
            }
            return hostAddress;
        } catch (SocketException ex) {
            LOGGER.error("Could not retrieve network interface: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Get all broadcast addresses on the current host
     *
     * @return list of broadcast addresses, empty list if no broadcast addresses found
     */
    public static List<String> getAllBroadcastAddresses() {
        List<String> broadcastAddresses = new LinkedList<String>();
        try {
            final Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                final NetworkInterface networkInterface = networkInterfaces.nextElement();
                final List<InterfaceAddress> interfaceAddresses = networkInterface.getInterfaceAddresses();
                for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                    final InetAddress addr = interfaceAddress.getAddress();
                    if (!addr.isLinkLocalAddress() && !addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        broadcastAddresses.add(interfaceAddress.getBroadcast().getHostAddress());
                    }
                }
            }
        } catch (SocketException ex) {
            LOGGER.error("Could not find broadcast address: {}", ex.getMessage(), ex);
        }
        return broadcastAddresses;
    }

    @Override
    public @Nullable String getConfiguredBroadcastAddress() {
        String broadcastAddr;

        if (configuredBroadcastAddress != null) {
            broadcastAddr = configuredBroadcastAddress;
        } else {
            // we do not seem to have any network interfaces
            broadcastAddr = null;
        }
        return broadcastAddr;
    }

    private @Nullable String getPrimaryBroadcastAddress() {
        String primaryIp = getPrimaryIpv4HostAddress();
        String broadcastAddress = null;
        if (primaryIp != null) {
            try {
                Short prefix = getAllInterfaceAddresses().stream()
                        .filter(a -> a.getAddress().getHostAddress().equals(primaryIp)).map(a -> a.getPrefix())
                        .findFirst().get().shortValue();
                broadcastAddress = getIpv4NetBroadcastAddress(primaryIp, prefix);
            } catch (IllegalArgumentException ex) {
                LOGGER.error("Invalid IP address parameter: {}", ex.getMessage(), ex);
            }
        }
        if (broadcastAddress == null) {
            // an error has occurred, using broadcast address of first interface instead
            broadcastAddress = getFirstIpv4BroadcastAddress();
            LOGGER.warn(
                    "Could not find broadcast address of primary IP, using broadcast address {} of first interface instead",
                    broadcastAddress);
        }
        return broadcastAddress;
    }

    /**
     * @deprecated Please use the NetworkAddressService with {@link #getConfiguredBroadcastAddress()}
     *
     *             Get the first candidate for a broadcast address
     *
     * @return broadcast address, null if no broadcast address is found
     */
    @Deprecated
    public static @Nullable String getBroadcastAddress() {
        final List<String> broadcastAddresses = getAllBroadcastAddresses();
        if (!broadcastAddresses.isEmpty()) {
            return broadcastAddresses.get(0);
        } else {
            return null;
        }
    }

    private static @Nullable String getFirstIpv4BroadcastAddress() {
        final List<String> broadcastAddresses = getAllBroadcastAddresses();
        if (!broadcastAddresses.isEmpty()) {
            return broadcastAddresses.get(0);
        } else {
            return null;
        }
    }

    /**
     * Gets every IPv4+IPv6 Address on each Interface except the loopback interface.
     * The Address format is in the CIDR notation which is ip/prefix-length e.g. 129.31.31.1/24.
     *
     * Example to get a list of only IPv4 addresses in string representation:
     * List<String> l = getAllInterfaceAddresses().stream().filter(a->a.getAddress() instanceof
     * Inet4Address).map(a->a.getAddress().getHostAddress()).collect(Collectors.toList());
     *
     * @return The collected IPv4 and IPv6 Addresses
     */
    public static Collection<CidrAddress> getAllInterfaceAddresses() {
        Collection<CidrAddress> interfaceIPs = new ArrayList<>();
        Enumeration<NetworkInterface> en;
        try {
            en = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException ex) {
            LOGGER.error("Could not find interface IP addresses: {}", ex.getMessage(), ex);
            return interfaceIPs;
        }

        while (en.hasMoreElements()) {
            NetworkInterface networkInterface = en.nextElement();

            try {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
            } catch (SocketException ignored) {
                continue;
            }

            for (InterfaceAddress cidr : networkInterface.getInterfaceAddresses()) {
                final InetAddress address = cidr.getAddress();
                assert address != null; // NetworkInterface.getInterfaceAddresses() should return only non-null
                                        // addresses
                interfaceIPs.add(new CidrAddress(address, cidr.getNetworkPrefixLength()));
            }
        }

        return interfaceIPs;
    }

    /**
     * Converts a netmask in bits into a string representation
     * i.e. 24 bits -> 255.255.255.0
     *
     * @param prefixLength bits of the netmask
     * @return string representation of netmask (i.e. 255.255.255.0)
     */
    public static String networkPrefixLengthToNetmask(int prefixLength) {
        if (prefixLength > 32 || prefixLength < 1) {
            throw new IllegalArgumentException("Network prefix length is not within bounds");
        }

        int ipv4Netmask = 0xFFFFFFFF;
        ipv4Netmask <<= (32 - prefixLength);

        byte[] octets = new byte[] { (byte) (ipv4Netmask >>> 24), (byte) (ipv4Netmask >>> 16),
                (byte) (ipv4Netmask >>> 8), (byte) ipv4Netmask };

        String result = "";
        for (int i = 0; i < 4; i++) {
            result += octets[i] & 0xff;
            if (i < 3) {
                result += ".";
            }
        }
        return result;
    }

    /**
     * Get the network address a specific ip address is in
     *
     * @param ipAddressString ipv4 address of the device (i.e. 192.168.5.1)
     * @param netMask netmask in bits (i.e. 24)
     * @return network a device is in (i.e. 192.168.5.0)
     * @throws IllegalArgumentException if parameters are wrong
     */
    public static String getIpv4NetAddress(String ipAddressString, short netMask) {
        String errorString = "IP '" + ipAddressString + "' is not a valid IPv4 address";
        if (!isValidIPConfig(ipAddressString)) {
            throw new IllegalArgumentException(errorString);
        }
        if (netMask < 1 || netMask > 32) {
            throw new IllegalArgumentException("Netmask '" + netMask + "' is out of bounds (1-32)");
        }

        String subnetMaskString = networkPrefixLengthToNetmask(netMask);

        String[] netMaskOctets = subnetMaskString.split("\\.");
        String[] ipv4AddressOctets = ipAddressString.split("\\.");
        String netAddress = "";
        try {
            for (int i = 0; i < 4; i++) {
                netAddress += Integer.parseInt(ipv4AddressOctets[i]) & Integer.parseInt(netMaskOctets[i]);
                if (i < 3) {
                    netAddress += ".";
                }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(errorString);
        }

        return netAddress;
    }

    /**
     * Get the network broadcast address of the subnet a specific ip address is in
     *
     * @param ipAddressString ipv4 address of the device (i.e. 192.168.5.1)
     * @param prefix network prefix in bits (i.e. 24)
     * @return network broadcast address of the network the device is in (i.e. 192.168.5.255)
     * @throws IllegalArgumentException if parameters are wrong
     */
    public static String getIpv4NetBroadcastAddress(String ipAddressString, short prefix) {
        String errorString = "IP '" + ipAddressString + "' is not a valid IPv4 address";
        if (!isValidIPConfig(ipAddressString)) {
            throw new IllegalArgumentException(errorString);
        }
        if (prefix < 1 || prefix > 32) {
            throw new IllegalArgumentException("Prefix '" + prefix + "' is out of bounds (1-32)");
        }

        try {
            byte[] addr = InetAddress.getByName(ipAddressString).getAddress();
            byte[] netmask = InetAddress.getByName(networkPrefixLengthToNetmask(prefix)).getAddress();
            byte[] broadcast = new byte[] { (byte) (~netmask[0] | addr[0]), (byte) (~netmask[1] | addr[1]),
                    (byte) (~netmask[2] | addr[2]), (byte) (~netmask[3] | addr[3]) };
            return InetAddress.getByAddress(broadcast).getHostAddress();
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException(errorString);
        }
    }

    private @Nullable String getIPv4inSubnet(String ipAddress, String subnetMask) {
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface current = interfaces.nextElement();
                if (!current.isUp() || current.isLoopback() || current.isVirtual() || current.isPointToPoint()) {
                    continue;
                }

                for (InterfaceAddress ifAddr : current.getInterfaceAddresses()) {
                    InetAddress addr = ifAddr.getAddress();

                    if (addr.isLoopbackAddress() || (addr instanceof Inet6Address)) {
                        continue;
                    }

                    String ipv4AddressOnInterface = addr.getHostAddress();
                    String subnetStringOnInterface = getIpv4NetAddress(ipv4AddressOnInterface,
                            ifAddr.getNetworkPrefixLength()) + "/" + String.valueOf(ifAddr.getNetworkPrefixLength());

                    String configuredSubnetString = getIpv4NetAddress(ipAddress, Short.parseShort(subnetMask)) + "/"
                            + subnetMask;

                    // use first IP within this subnet
                    if (subnetStringOnInterface.equals(configuredSubnetString)) {
                        return ipv4AddressOnInterface;
                    }
                }
            }
        } catch (SocketException ex) {
            LOGGER.error("Could not retrieve network interface: {}", ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * Checks if the given String is a valid IPv4 Address
     * or IPv4 address in CIDR notation
     *
     * @param ipAddress in format xxx.xxx.xxx.xxx or xxx.xxx.xxx.xxx/xx
     * @return true if it is a valid address
     */
    public static boolean isValidIPConfig(String ipAddress) {
        if (ipAddress.contains("/")) {
            String parts[] = ipAddress.split("/");
            boolean ipMatches = IPV4_PATTERN.matcher(parts[0]).matches();

            int netMask = Integer.parseInt(parts[1]);
            boolean netMaskMatches = false;
            if (netMask > 0 || netMask < 32) {
                netMaskMatches = true;
            }

            if (ipMatches && netMaskMatches) {
                return true;
            }
        } else {
            return IPV4_PATTERN.matcher(ipAddress).matches();
        }
        return false;
    }

}
