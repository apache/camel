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
package org.apache.camel.component.ssh;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.config.hosts.HostConfigEntryResolver;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A server reached at the IPv6 address ::1 is looked up as [::1]:port, as the address is written, although the address
 * the client connects to writes it as 0:0:0:0:0:0:0:1: both when the endpoint is configured with ::1, and when the
 * endpoint is configured with an alias that the host configuration of the client (as ~/.ssh/config would) gives the
 * HostName ::1 for. The host is set on the endpoint, as the endpoint uri does not accept an IPv6 address.
 */
class SshComponentKnownHostIPv6Test extends SshComponentTestSupport {

    private static final String ALIAS = "review-ipv6";

    private String knownHostsServerKey;
    private String knownHostsOtherKey;

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();
        assumeTrue(isIPv6LoopbackAvailable(), "The IPv6 loopback address is not available");
        knownHostsServerKey = writeKnownHosts("known_hosts_server_key", "[::1]:" + port + " " + serverPublicKey());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String otherKey = PublicKeyEntry.toString(generator.generateKeyPair().getPublic());
        knownHostsOtherKey = writeKnownHosts("known_hosts_other_key", "[::1]:" + port + " " + otherKey);
    }

    @Test
    void testProducerWithTheServerKey() throws Exception {
        assertDelivered("serverKey");
    }

    @Test
    void testProducerWithTheServerKeyAcceptingUnknownHosts() throws Exception {
        assertDelivered("serverKeyAcceptingUnknownHosts");
    }

    @Test
    void testProducerWithAnotherKey() throws Exception {
        assertRejected("otherKey");
    }

    @Test
    void testProducerWithAnotherKeyAcceptingUnknownHosts() throws Exception {
        assertRejected("otherKeyAcceptingUnknownHosts");
    }

    @Test
    void testProducerWithAnAliasAndTheServerKey() throws Exception {
        assertDelivered("aliasServerKey");
    }

    @Test
    void testProducerWithAnAliasAndTheServerKeyAcceptingUnknownHosts() throws Exception {
        assertDelivered("aliasServerKeyAcceptingUnknownHosts");
    }

    @Test
    void testProducerWithAnAliasAndAnotherKey() throws Exception {
        assertRejected("aliasOtherKey");
    }

    @Test
    void testProducerWithAnAliasAndAnotherKeyAcceptingUnknownHosts() throws Exception {
        assertRejected("aliasOtherKeyAcceptingUnknownHosts");
    }

    /*
     * Each route uses its own user name, which the test server records when it receives credentials.
     */
    private void assertDelivered(String route) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("test");
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBody("direct:" + route, "test");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(authenticatedUsers.contains(route), "the server should receive the credentials");
    }

    private void assertRejected(String route) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:" + route, "test");

        MockEndpoint.assertIsSatisfied(context);
        assertFalse(authenticatedUsers.contains(route), "the server should not receive any credentials");
    }

    private boolean isIPv6LoopbackAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("::1", port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /*
     * The host configuration of the client, as ~/.ssh/config would give it: Host review-ipv6, HostName ::1. It answers
     * only once, so the host key must be checked against the host that the connection was resolved to, without asking
     * the host configuration again.
     */
    private static HostConfigEntryResolver resolvingOnce() {
        AtomicBoolean resolved = new AtomicBoolean();
        return (host, hostPort, localAddress, username, proxyJump, attributes) -> {
            if (!ALIAS.equals(host)) {
                return null;
            }
            if (resolved.getAndSet(true)) {
                throw new IOException("The host configuration answers only once");
            }
            return new HostConfigEntry(host, "::1", hostPort, username);
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                route("serverKey", "::1", knownHostsServerKey, true);
                route("serverKeyAcceptingUnknownHosts", "::1", knownHostsServerKey, false);
                route("otherKey", "::1", knownHostsOtherKey, true);
                route("otherKeyAcceptingUnknownHosts", "::1", knownHostsOtherKey, false);
                route("aliasServerKey", ALIAS, knownHostsServerKey, true);
                route("aliasServerKeyAcceptingUnknownHosts", ALIAS, knownHostsServerKey, false);
                route("aliasOtherKey", ALIAS, knownHostsOtherKey, true);
                route("aliasOtherKeyAcceptingUnknownHosts", ALIAS, knownHostsOtherKey, false);
            }

            private void route(String name, String host, String knownHosts, boolean failOnUnknownHost) {
                SshEndpoint endpoint = context.getEndpoint("ssh://" + name + ":secret@localhost:" + port
                                                           + "?timeout=3000&knownHostsResource=" + knownHosts
                                                           + "&failOnUnknownHost=" + failOnUnknownHost,
                        SshEndpoint.class);
                endpoint.setHost(host);
                endpoint.getConfiguration()
                        .setClientBuilder(ClientBuilder.builder().hostConfigEntryResolver(resolvingOnce()));
                from("direct:" + name).to(endpoint).to("mock:result");
            }
        };
    }
}
