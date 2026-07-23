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
package org.apache.camel.component.a2a.push;

import java.net.InetAddress;
import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a push notification request is targeted at the address the webhook URL was validated against, while
 * still carrying the original hostname so the Host header, TLS SNI and certificate verification are unaffected.
 */
class PushNotificationTargetPinningTest {

    @Test
    void targetCarriesValidatedAddressAndOriginalHostname() throws Exception {
        InetAddress validated = InetAddress.getByName("203.0.113.10");

        HttpHost target = PushNotificationDispatcher.pinnedTarget(
                URI.create("https://webhook.example.com/events"), validated);

        // the connection is opened to the address that passed the SSRF checks, not to a fresh DNS lookup
        assertThat(target.getAddress()).isEqualTo(validated);
        // the hostname is preserved for Host header / TLS SNI / certificate hostname verification
        assertThat(target.getHostName()).isEqualTo("webhook.example.com");
        assertThat(target.getSchemeName()).isEqualTo("https");
        assertThat(target.getPort()).isEqualTo(443);
    }

    @Test
    void targetUsesDefaultPortPerScheme() throws Exception {
        InetAddress validated = InetAddress.getByName("203.0.113.11");

        assertThat(PushNotificationDispatcher.pinnedTarget(URI.create("https://host.example/x"), validated).getPort())
                .isEqualTo(443);
        assertThat(PushNotificationDispatcher.pinnedTarget(URI.create("http://host.example/x"), validated).getPort())
                .isEqualTo(80);
    }

    @Test
    void targetHonoursExplicitPort() throws Exception {
        InetAddress validated = InetAddress.getByName("203.0.113.12");

        HttpHost target = PushNotificationDispatcher.pinnedTarget(
                URI.create("https://host.example:8443/x"), validated);

        assertThat(target.getPort()).isEqualTo(8443);
        assertThat(target.getAddress()).isEqualTo(validated);
    }
}
