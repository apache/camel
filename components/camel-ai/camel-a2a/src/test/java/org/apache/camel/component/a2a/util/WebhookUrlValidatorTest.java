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

import org.apache.camel.component.a2a.util.WebhookUrlValidator.HostResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    /**
     * A resolver that maps any host to a fixed address, so the resolved-host path can be exercised without depending on
     * DNS. The address carries the original host name, exactly as a real lookup would return it.
     */
    private static HostResolver resolvingTo(String literal) {
        return host -> InetAddress.getByAddress(host, InetAddress.getByName(literal).getAddress());
    }

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
                .hasMessageContaining("loopback");
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
    void rejectsSharedAddressSpace() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://100.64.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carrier-grade NAT");

        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://100.127.255.254/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carrier-grade NAT");
    }

    @Test
    void acceptsAddressesAdjacentToSharedAddressSpace() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                        "https://webhook.example/hook", false, resolvingTo("100.63.255.255")));

        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                        "https://webhook.example/hook", false, resolvingTo("100.128.0.1")));
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

    // ---- IPv6 ranges ----

    @Test
    void rejectsIpv6UniqueLocalAddress() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fd00::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique local");

        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fc00::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique local");

        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fdff:ffff::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique local");
    }

    /**
     * A unique local address must be rejected when it is reached through a host name, not only when it is written as a
     * literal. {@code InetAddress#isSiteLocalAddress} does not report {@code fc00::/7}, so this is the case that a
     * predicate-only classification lets through.
     */
    @Test
    void rejectsHostnameResolvingToUniqueLocalAddress() {
        assertThatThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                "https://webhook.example/hook", false, resolvingTo("fd00::1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique local");

        assertThatThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                "https://webhook.example/hook", false, resolvingTo("fc00::1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique local");
    }

    @Test
    void rejectsIpv6SiteLocalAddress() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fec0::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");
    }

    @Test
    void rejectsIpv6LinkLocal() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fe80::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("link-local");
    }

    @Test
    void rejectsIpv6Wildcard() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[::]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }

    @Test
    void rejectsIpv4MappedIpv6() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[::ffff:10.0.0.1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");
    }

    @Test
    void rejectsIpv4CompatibleIpv6() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[::10.0.0.1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local/private (embedded IPv4)");
    }

    // ---- Transition mechanisms that carry an IPv4 address ----

    @Test
    void rejectsNat64EmbeddingPrivateIpv4() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[64:ff9b::a00:1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local/private (embedded IPv4)");
    }

    @Test
    void rejectsNat64EmbeddingLoopback() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[64:ff9b::7f00:1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback (embedded IPv4)");
    }

    /**
     * NAT64 is how an IPv6-only network reaches the IPv4 internet, so a prefix carrying a public address stays allowed.
     */
    @Test
    void acceptsNat64EmbeddingPublicIpv4() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validate("https://[64:ff9b::808:808]/webhook"));
    }

    @Test
    void rejects6to4EmbeddingPrivateIpv4() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[2002:c0a8:101::1]/webhook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local/private (embedded IPv4)");
    }

    @Test
    void accepts6to4EmbeddingPublicIpv4() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validate("https://[2002:808:808::1]/webhook"));
    }

    // ---- Host names are not classified by their spelling ----

    /**
     * Host names are classified by the address they resolve to, never by how they are spelled. Names beginning with the
     * hex digits of the IPv6 private prefixes, such as {@code fcm.} or {@code fd-}, are ordinary public host names.
     */
    @Test
    void acceptsHostnamesSpelledLikePrivateIpv6Prefixes() {
        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                        "https://fcm.example.test/webhook", false, resolvingTo("93.184.216.34")));

        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                        "https://fd-edge.example.test/webhook", false, resolvingTo("93.184.216.34")));

        assertThatNoException()
                .isThrownBy(() -> WebhookUrlValidator.validateAndResolve(
                        "https://fe80-cdn.example.test/webhook", false, resolvingTo("93.184.216.34")));
    }

    // ---- Private ranges still blocked even with allowLocal ----

    @Test
    void privateIpStillBlockedWhenLocalAllowed() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://192.168.1.1/webhook", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");
    }

    @Test
    void uniqueLocalStillBlockedWhenLocalAllowed() {
        assertThatThrownBy(() -> WebhookUrlValidator.validate("https://[fd00::1]/webhook", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique local");
    }
}
