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
package org.apache.camel.component.dns.policy;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Check if a hostname resolves to a specified cname or an ip
 */
public class DnsActivation {
    private static final transient String[] DNS_TYPES = { "CNAME", "A" };
    private static final transient Logger LOG = LoggerFactory.getLogger(DnsActivation.class);

    private String hostname;
    private List<String> resolvesTo = new ArrayList<>();

    public DnsActivation() {
    }

    public DnsActivation(String hostname, List<String> resolvesTo) {
        this.hostname = hostname;
        this.resolvesTo.addAll(resolvesTo);
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getHostname() {
        return hostname;
    }

    public void setResolvesTo(List<String> resolvesTo) {
        this.resolvesTo = new ArrayList<>();
        this.resolvesTo.addAll(resolvesTo);
    }

    public void setResolvesTo(String resolvesTo) {
        this.resolvesTo = new ArrayList<>();
        this.resolvesTo.add(resolvesTo);
    }

    public List<String> getResolvesTo() {
        return resolvesTo;
    }

    public boolean isActive() throws Exception {
        if (resolvesTo.isEmpty()) {
            try {
                resolvesTo.addAll(getLocalIps());
            } catch (Exception e) {
                LOG.warn("Failed to get local ips and resolvesTo not specified. Identifying as inactive.", e);
                throw e;
            }
        }

        LOG.debug("Resolving {}", hostname);
        List<String> hostnames = new ArrayList<>();
        hostnames.add(hostname);

        List<String> resolved = new ArrayList<>();
        while (!hostnames.isEmpty()) {
            NamingEnumeration<?> attributeEnumeration = null;
            try {
                String hostname = hostnames.remove(0);
                InetAddress inetAddress = InetAddress.getByName(hostname);
                InitialDirContext initialDirContext = new InitialDirContext();
                Attributes attributes = initialDirContext.getAttributes("dns:/" + inetAddress.getHostName(), DNS_TYPES);
                attributeEnumeration = attributes.getAll();
                while (attributeEnumeration.hasMore()) {
                    Attribute attribute = (Attribute) attributeEnumeration.next();
                    String id = attribute.getID();
                    String value = (String) attribute.get();
                    if (resolvesTo.contains(value)) {
                        LOG.debug("{} = {} matched. Identifying as active.", id, value);
                        return true;
                    }
                    LOG.debug("{} = {}", id, value);
                    if (id.equals("CNAME") && !resolved.contains(value)) {
                        hostnames.add(value);
                    }
                    resolved.add(value);
                }
            } catch (Exception e) {
                LOG.warn(hostname, e);
                throw e;
            } finally {
                if (attributeEnumeration != null) {
                    try {
                        attributeEnumeration.close();
                    } catch (Exception e) {
                        LOG.warn("Failed to close attributeEnumeration. Memory leak possible.", e);
                    }
                    attributeEnumeration = null;
                }
            }
        }

        return false;
    }

    private List<String> getLocalIps() throws Exception {
        List<String> localIps = new ArrayList<>();

        Enumeration<NetworkInterface> networkInterfacesEnumeration = NetworkInterface.getNetworkInterfaces();
        while (networkInterfacesEnumeration.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfacesEnumeration.nextElement();

            Enumeration<InetAddress> inetAddressesEnumeration = networkInterface.getInetAddresses();
            while (inetAddressesEnumeration.hasMoreElements()) {
                InetAddress inetAddress = inetAddressesEnumeration.nextElement();
                String ip = inetAddress.getHostAddress();
                LOG.debug("Local ip: {}", ip);
                localIps.add(ip);
            }
        }
        return localIps;
    }
}
