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

/**
 * Validates webhook URLs for SSRF protection in A2A push notifications. All hostnames (including {@code localhost}) are
 * resolved to their IP address and classified consistently. Loopback addresses are blocked by default; set
 * {@code allowLocal=true} for local development.
 */
public final class WebhookUrlValidator {

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

        // Block private IPv6 ranges before DNS resolution
        if (isPrivateIpv6(host)) {
            throw new IllegalArgumentException(
                    "Webhook URL must not point to private/internal IPv6 ranges (SSRF protection): " + host);
        }

        // Resolve hostname to IP — treats localhost, 127.0.0.1, and any hostname the same way
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
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
            return;
        }

        // Non-loopback: require HTTPS
        if (scheme.equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("Webhook URL must use HTTPS for non-localhost hosts");
        }

        if (address.isAnyLocalAddress()) {
            throw new IllegalArgumentException(
                    "Webhook URL must not point to a wildcard address (SSRF protection): " + host);
        }
        if (address.isLinkLocalAddress()) {
            throw new IllegalArgumentException(
                    "Webhook URL must not point to a link-local address (SSRF protection): " + host);
        }
        if (address.isSiteLocalAddress()) {
            throw new IllegalArgumentException(
                    "Webhook URL must not point to a site-local/private address (SSRF protection): " + host);
        }
    }

    private static boolean isPrivateIpv6(String host) {
        String lower = host.toLowerCase();
        if (lower.startsWith("[") && lower.endsWith("]")) {
            lower = lower.substring(1, lower.length() - 1);
        }
        if (lower.equals("::1")) {
            return true;
        }
        // Unique Local Address (fc00::/7)
        if (lower.startsWith("fc") || lower.startsWith("fd")) {
            return true;
        }
        // Link-local (fe80::/10)
        if (lower.startsWith("fe80:")) {
            return true;
        }
        // IPv4-mapped IPv6 (::ffff:x.x.x.x)
        if (lower.startsWith("::ffff:")) {
            return true;
        }
        return false;
    }
}
