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
package org.apache.camel.component.a2a.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Validates webhook URLs for SSRF protection in A2A push notifications. Every host, whether it is written as an IP
 * literal or as a name that has to be resolved, is turned into an address and classified by the same rules, so the two
 * forms can never disagree. Loopback addresses are blocked by default; set {@code allowLocal=true} for local
 * development.
 */
public final class WebhookUrlValidator {

    private static final int IPV4_LENGTH = 4;

    /** 64:ff9b::/96, the well-known prefix for IPv4/IPv6 translation (RFC 6052). */
    private static final byte[] NAT64_WELL_KNOWN_PREFIX
            = { 0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0 };

    private WebhookUrlValidator() {
    }

    /**
     * Validates with loopback blocked (production default).
     */
    public static void validate(String url) {
        validate(url, false);
    }

    /**
     * Validates a webhook URL for SSRF protection.
     *
     * @param  url                      the URL to validate
     * @param  allowLocal               whether to permit loopback/localhost addresses (dev mode)
     * @throws IllegalArgumentException if the URL is invalid or unsafe
     */
    public static void validate(String url, boolean allowLocal) {
        validateAndResolve(url, allowLocal);
    }

    /**
     * Validates a webhook URL for SSRF protection and returns the address the host resolved to during validation.
     * <p>
     * Callers that go on to open a connection should connect to the returned address rather than letting the HTTP
     * client resolve the hostname again, otherwise the address that was validated and the address actually connected to
     * can differ for the same hostname (DNS rebinding), defeating the checks performed here.
     *
     * @param  url                      the URL to validate
     * @param  allowLocal               whether to permit loopback/localhost addresses (dev mode)
     * @return                          the validated address the host resolved to
     * @throws IllegalArgumentException if the URL is invalid or unsafe
     */
    public static InetAddress validateAndResolve(String url, boolean allowLocal) {
        return validateAndResolve(url, allowLocal, InetAddress::getByName);
    }

    /**
     * Validates against a supplied resolver, so the resolved-host path can be exercised without depending on DNS.
     */
    static InetAddress validateAndResolve(String url, boolean allowLocal, HostResolver resolver) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be null or empty");
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + e.getMessage(), e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Webhook URL must have a scheme (http or https)");
        }
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Webhook URL must use http or https scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must have a host");
        }

        // Resolve the host to an IP — IP literals, localhost and any other name are all treated the same way,
        // and the address that comes back is what the remaining checks classify
        InetAddress address;
        try {
            address = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                    "Webhook URL host cannot be resolved: " + host, e);
        }

        if (address.isLoopbackAddress()) {
            if (!allowLocal) {
                throw new IllegalArgumentException(
                        "Webhook URL must not point to a loopback address (SSRF protection): " + host
                                                   + ". Set allowLocalWebhookUrls=true for local development.");
            }
            // Loopback allowed — skip remaining network checks, permit HTTP
            return address;
        }

        // Non-loopback: require HTTPS
        if (scheme.equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS for non-localhost hosts");
        }

        String reason = nonGlobalReason(address);
        if (reason != null) {
            throw new IllegalArgumentException(
                    "Webhook URL must not point to a " + reason + " address (SSRF protection): " + host);
        }

        return address;
    }

    /**
     * Describes the non-global range an address falls in, or returns {@code null} when it is an ordinary globally
     * routable address.
     * <p>
     * {@link InetAddress} carries predicates for most of these ranges but not all of them:
     * {@link InetAddress#isSiteLocalAddress()} reports only the deprecated {@code fec0::/10} block and not the
     * {@code fc00::/7} unique local addresses that replaced it, there is no predicate for the shared address space, and
     * none for the transition mechanisms that carry an IPv4 address inside an IPv6 one. Those are classified here from
     * the raw address bytes.
     */
    static String nonGlobalReason(InetAddress address) {
        // Loopback is reported here for the sake of the addresses that carry an IPv4 address inside an IPv6 one:
        // a host reaching loopback directly is answered earlier, where allowLocal can let it through
        if (address.isLoopbackAddress()) {
            return "loopback";
        }
        if (address.isAnyLocalAddress()) {
            return "wildcard";
        }
        if (address.isLinkLocalAddress()) {
            return "link-local";
        }
        if (address.isSiteLocalAddress()) {
            return "site-local/private";
        }
        byte[] bytes = address.getAddress();
        return bytes.length == IPV4_LENGTH ? ipv4Reason(bytes) : ipv6Reason(bytes);
    }

    private static String ipv4Reason(byte[] bytes) {
        // 100.64.0.0/10, the shared address space used for carrier-grade NAT (RFC 6598)
        if ((bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 0x40) {
            return "carrier-grade NAT";
        }
        return null;
    }

    private static String ipv6Reason(byte[] bytes) {
        // fc00::/7, the unique local addresses that replaced the deprecated fec0::/10 site-local block
        if ((bytes[0] & 0xfe) == 0xfc) {
            return "unique local";
        }
        // ::a.b.c.d and ::ffff:a.b.c.d hold an IPv4 address in the low 32 bits
        if (isZero(bytes, 0, 10) && (isZero(bytes, 10, 12) || isOnes(bytes, 10, 12))) {
            return embeddedIpv4Reason(bytes, 12);
        }
        // 64:ff9b::/96 translates an IPv4 address held in the low 32 bits
        if (hasPrefix(bytes, NAT64_WELL_KNOWN_PREFIX)) {
            return embeddedIpv4Reason(bytes, 12);
        }
        // 2002::/16 carries the IPv4 address of the 6to4 endpoint in bytes 2 to 5
        if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x02) {
            return embeddedIpv4Reason(bytes, 2);
        }
        return null;
    }

    private static String embeddedIpv4Reason(byte[] bytes, int offset) {
        InetAddress embedded;
        try {
            embedded = InetAddress.getByAddress(Arrays.copyOfRange(bytes, offset, offset + IPV4_LENGTH));
        } catch (UnknownHostException e) {
            // Not reachable: getByAddress only rejects arrays that are neither 4 nor 16 bytes long
            throw new IllegalStateException("Unexpected address length", e);
        }
        String reason = nonGlobalReason(embedded);
        return reason == null ? null : reason + " (embedded IPv4)";
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOnes(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != (byte) 0xff) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasPrefix(byte[] bytes, byte[] prefix) {
        return Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    /**
     * Resolves a host, which may be a name or an IP literal, to the address a connection would be opened to.
     */
    @FunctionalInterface
    interface HostResolver {
        InetAddress resolve(String host) throws UnknownHostException;
    }
}
