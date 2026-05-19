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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    @Test
    void acceptsHttpsUrl() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validate("https://example.com/webhooks/a2a"));
    }

    // ---- Loopback blocked by default ----

    @Test
    void rejectsLocalhostByDefault() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http://localhost:8080/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void rejectsLoopbackIpByDefault() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://127.0.0.1:8080/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void rejectsIpv6LoopbackByDefault() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[::1]:8080/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IPv6");
    }

    // ---- Loopback allowed with flag ----

    @Test
    void acceptsLocalhostHttpWhenAllowed() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validate("http://localhost:8080/webhook", true));
    }

    @Test
    void acceptsLoopbackIpWhenAllowed() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validate("http://127.0.0.1:8080/webhook", true));
    }

    // ---- Non-localhost HTTP blocked ----

    @Test
    void rejectsNonLocalhostHttp() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("http://example.com/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    // ---- Private/internal ranges always blocked ----

    @Test
    void rejectsPrivateIpRanges() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://10.0.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");

        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://192.168.1.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");

        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://172.16.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");
    }

    @Test
    void rejectsFullLoopbackRange() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://127.0.0.2/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");

        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://127.255.255.254/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void rejectsWildcardAddress() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://0.0.0.0/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsLinkLocalRange() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("link-local");
    }

    @Test
    void rejectsUnresolvableHost() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://this-host-does-not-exist-xyzzy.invalid/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void rejectsNullOrEmpty() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookUrlValidator.validate(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIpv6UniqueLocalAddress() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fd00::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IPv6");
    }

    @Test
    void rejectsIpv6LinkLocal() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fe80::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IPv6");
    }

    @Test
    void rejectsIpv4MappedIpv6() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[::ffff:10.0.0.1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IPv6");
    }

    // ---- Private ranges still blocked even with allowLocal ----

    @Test
    void privateIpStillBlockedWhenLocalAllowed() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://192.168.1.1/webhook", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");
    }
}
