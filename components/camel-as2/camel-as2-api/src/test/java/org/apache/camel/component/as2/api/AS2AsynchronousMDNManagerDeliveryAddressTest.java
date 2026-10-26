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
package org.apache.camel.component.as2.api;

import javax.net.ssl.SSLContext;

import org.apache.camel.component.as2.api.entity.MultipartMimeEntity;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delivery address for an asynchronous MDN comes from the {@code Receipt-Delivery-Option} header of the received
 * AS2 message, so it is chosen by the sender. It selects an outbound destination and, before this was constrained, the
 * configured MDN credentials went with it.
 */
class AS2AsynchronousMDNManagerDeliveryAddressTest {

    private static final String ALLOWED = "partner.example";

    @Test
    void aSchemeOtherThanHttpOrHttpsIsRefused() {
        for (String address : new String[] {
                "file:///etc/passwd", "ftp://partner.example/x", "gopher://partner.example:70/x", "//partner.example/x" }) {
            HttpException e = assertThrows(HttpException.class, () -> deliver(address, ALLOWED),
                    "expected " + address + " to be refused");
            assertTrue(e.getMessage().contains("must use http or https") || e.getMessage().contains("no host"),
                    "unexpected message for " + address + ": " + e.getMessage());
        }
    }

    /**
     * https is fail-closed: without SSLContextParameters the manager has no way to deliver over TLS, so it must refuse
     * rather than write the MDN and the Authorization header in cleartext to the TLS port. This preserves the behaviour
     * introduced by CAMEL-24417 for a deployment that has not opted in.
     */
    @Test
    void httpsWithoutAnSslContextIsRefused() {
        for (String address : new String[] {
                "https://partner.example/receipts", "https://partner.example:443/receipts" }) {
            HttpException e = assertThrows(HttpException.class, () -> deliver(address, ALLOWED),
                    "expected " + address + " to be refused without an SSLContext");
            assertTrue(e.getMessage().contains("https") && e.getMessage().contains("SSLContextParameters"),
                    "unexpected message for " + address + ": " + e.getMessage());
        }
    }

    /**
     * With an SSLContext configured, an https address is accepted: it gets past the scheme checks and fails on the
     * connection instead, which is what tells us the address itself was accepted.
     */
    @Test
    void httpsWithAnSslContextIsAccepted() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        Exception e = assertThrows(Exception.class,
                () -> deliver("https://localhost:1/receipts", ALLOWED, sslContext));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("must use http"),
                "an https address must be accepted when an SSLContext is configured: " + e.getMessage());
    }

    @Test
    void aHostOutsideTheAllowListIsRefused() {
        HttpException e = assertThrows(HttpException.class,
                () -> deliver("http://attacker.example/receipts", ALLOWED));
        assertTrue(e.getMessage().contains("asyncMdnAllowedHosts"), "unexpected message: " + e.getMessage());
    }

    /**
     * With no allow-list the MDN is still delivered, so this gets past the address checks and fails on the connection
     * instead - which is what tells us the address itself was accepted.
     */
    @Test
    void withNoAllowListTheAddressIsStillAccepted() {
        Exception e = assertThrows(Exception.class,
                () -> deliver("http://localhost:1/receipts", null));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnAllowedHosts"),
                "the address must not be refused when no allow-list is configured: " + e.getMessage());
    }

    @Test
    void anIpv6AllowListEntryIsMatchedOnHostAndPort() {
        // URI.getHost() yields "[::1]"; the operator lists the bracketed literal and its port as "[::1]:1"
        Exception e = assertThrows(Exception.class, () -> deliver("http://[::1]:1/receipts", "[::1]:1"));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnAllowedHosts"),
                "an IPv6 literal should match its allow-list entry on host and port: " + e.getMessage());
    }

    /**
     * An entry may pin the port in host:port form. A delivery address whose host and port both match the entry is
     * accepted, so it gets past the address checks and fails on the connection instead - which is what tells us the
     * address itself was accepted.
     */
    @Test
    void aMatchingHostAndPortIsAccepted() {
        Exception e = assertThrows(Exception.class, () -> deliver("http://localhost:1/receipts", "localhost:1"));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnAllowedHosts"),
                "a delivery matching an allow-list entry on host and port must be accepted: " + e.getMessage());
    }

    /**
     * The entry lists the host only, so it matches the default port (80 for http). A delivery to another port on that
     * host is refused before the connection is opened, and the credentials are not attached.
     */
    @Test
    void aDifferentPortOnAListedHostIsRefused() {
        HttpException e = assertThrows(HttpException.class,
                () -> deliver("http://partner.example:8443/receipts", ALLOWED));
        assertTrue(e.getMessage().contains("asyncMdnAllowedHosts"), "unexpected message: " + e.getMessage());
    }

    /**
     * A bare-host entry matches the default port for the scheme, so a delivery to that default port is accepted: it
     * gets past the address checks and fails on the connection instead.
     */
    @Test
    void aBareHostEntryMatchesTheDefaultPort() {
        Exception e = assertThrows(Exception.class, () -> deliver("http://partner.example/receipts", ALLOWED));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnAllowedHosts"),
                "a bare-host entry must match the default port: " + e.getMessage());
    }

    /**
     * With asyncMdnRequireHttps set, a plain-http delivery address is refused before the connection is opened and
     * before the credentials are attached, even when its host is on the allow-list, so the credentials are never
     * written in cleartext to a sender-chosen address.
     */
    @Test
    void httpIsRefusedWhenHttpsIsRequired() {
        HttpException e = assertThrows(HttpException.class,
                () -> deliver("http://partner.example/receipts", ALLOWED, null, true));
        assertTrue(e.getMessage().contains("asyncMdnRequireHttps"), "unexpected message: " + e.getMessage());
    }

    /**
     * With asyncMdnRequireHttps set, an https address (with an SSLContext) is accepted: it gets past the scheme checks
     * and fails on the connection instead, which is what tells us the address itself was accepted.
     */
    @Test
    void httpsIsAcceptedWhenHttpsIsRequired() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        Exception e = assertThrows(Exception.class,
                () -> deliver("https://localhost:1/receipts", "localhost:1", sslContext, true));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnRequireHttps"),
                "an https address must be accepted when asyncMdnRequireHttps is set: " + e.getMessage());
    }

    /**
     * By default asyncMdnRequireHttps is not set, so a plain-http delivery address is still accepted (it gets past the
     * scheme checks and fails on the connection instead): the control is opt-in and does not change the default.
     */
    @Test
    void httpIsAcceptedWhenHttpsIsNotRequired() {
        Exception e = assertThrows(Exception.class,
                () -> deliver("http://localhost:1/receipts", "localhost:1"));
        assertTrue(!(e instanceof HttpException) || !e.getMessage().contains("asyncMdnRequireHttps"),
                "http must be accepted when asyncMdnRequireHttps is not set: " + e.getMessage());
    }

    private static void deliver(String deliveryAddress, String allowedHosts) throws Exception {
        deliver(deliveryAddress, allowedHosts, null);
    }

    private static void deliver(String deliveryAddress, String allowedHosts, SSLContext sslContext) throws Exception {
        deliver(deliveryAddress, allowedHosts, sslContext, false);
    }

    private static void deliver(String deliveryAddress, String allowedHosts, SSLContext sslContext, boolean requireHttps)
            throws Exception {
        AS2AsynchronousMDNManager manager = new AS2AsynchronousMDNManager(
                "1.1", "Camel", "sender.example.com", null, null, "user", "password", null, allowedHosts, sslContext,
                requireHttps);
        manager.send(new TestEntity(), AS2MimeType.MULTIPART_REPORT, deliveryAddress);
    }

    /**
     * The manager null-checks its entity before it looks at the delivery address, so the address checks need a real
     * one. Its content is irrelevant here - none of these cases reach the point of writing it.
     */
    private static final class TestEntity extends MultipartMimeEntity {

        private TestEntity() {
            super(ContentType.create(AS2MimeType.MULTIPART_REPORT), "7bit");
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
