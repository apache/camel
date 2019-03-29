/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class HostUtils {

    private HostUtils() {
        //Utility Class
    }

    /**
     * Returns a {@link Map} of {@link InetAddress} per {@link NetworkInterface}.
     */
    public static Map<String, Set<InetAddress>> getNetworkInterfaceAddresses() {
        //JVM returns interfaces in a non-predictable order, so to make this more predictable
        //let's have them sort by interface name (by using a TreeMap).
        Map<String, Set<InetAddress>> interfaceAddressMap = new TreeMap<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                //We only care about usable non-loopback interfaces.
                if (iface.isUp() && !iface.isLoopback() && !iface.isPointToPoint()) {
                    String name = iface.getName();
                    Enumeration<InetAddress> ifaceAdresses = iface.getInetAddresses();
                    while (ifaceAdresses.hasMoreElements()) {
                        InetAddress ia = ifaceAdresses.nextElement();
                        //We want to filter out mac addresses
                        if (!ia.isLoopbackAddress() && !ia.getHostAddress().contains(":")) {
                            Set<InetAddress> addresses = interfaceAddressMap.get(name);
                            if (addresses == null) {
                                addresses = new LinkedHashSet<>();
                            }
                            addresses.add(ia);
                            interfaceAddressMap.put(name, addresses);
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            //noop
        }
        return interfaceAddressMap;
    }

    /**
     * Returns a {@link Set} of {@link InetAddress} that are non-loopback or mac.
     */
    public static Set<InetAddress> getAddresses() {
        Set<InetAddress> allAddresses = new LinkedHashSet<>();
        Map<String, Set<InetAddress>> interfaceAddressMap = getNetworkInterfaceAddresses();
        for (Map.Entry<String, Set<InetAddress>> entry : interfaceAddressMap.entrySet()) {
            Set<InetAddress> addresses = entry.getValue();
            if (!addresses.isEmpty()) {
                for (InetAddress address : addresses) {
                    allAddresses.add(address);
                }
            }
        }
        return allAddresses;
    }


    /**
     * Chooses one of the available {@link InetAddress} based on the specified preference.
     */
    private static InetAddress chooseAddress() throws UnknownHostException {
        Set<InetAddress> addresses = getAddresses();
        if (addresses.contains(InetAddress.getLocalHost())) {
            //Then if local host address is not bound to a loop-back interface, use it.
            return InetAddress.getLocalHost();
        } else if (addresses != null && !addresses.isEmpty()) {
            //else return the first available addrress
            return addresses.toArray(new InetAddress[addresses.size()])[0];
        } else {
            //else we are forcedt to use the localhost address.
            return InetAddress.getLocalHost();
        }
    }

    /**
     * Returns the local hostname. It loops through the network interfaces and returns the first non loopback hostname
     */
    public static String getLocalHostName() throws UnknownHostException {
        return chooseAddress().getHostName();
    }

    /**
     * Returns the local IP. It loops through the network interfaces and returns the first non loopback address
     */
    public static String getLocalIp() throws UnknownHostException {
        return chooseAddress().getHostAddress();
    }

}
