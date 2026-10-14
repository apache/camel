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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An endpoint configured with the IPv6 address ::1 is looked up as [::1]:port, as it is configured, although the
 * address the client connects to writes it as 0:0:0:0:0:0:0:1. The host is set on the endpoint, as the endpoint uri
 * does not accept an IPv6 address.
 */
class SshComponentKnownHostIPv6Test extends SshComponentTestSupport {

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
        assertDelivered("direct:serverKey");
    }

    @Test
    void testProducerWithTheServerKeyAcceptingUnknownHosts() throws Exception {
        assertDelivered("direct:serverKeyAcceptingUnknownHosts");
    }

    @Test
    void testProducerWithAnotherKey() throws Exception {
        assertRejected("direct:otherKey");
    }

    @Test
    void testProducerWithAnotherKeyAcceptingUnknownHosts() throws Exception {
        assertRejected("direct:otherKeyAcceptingUnknownHosts");
    }

    private void assertDelivered(String endpoint) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("test");
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBody(endpoint, "test");

        MockEndpoint.assertIsSatisfied(context);
    }

    private void assertRejected(String endpoint) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody(endpoint, "test");

        MockEndpoint.assertIsSatisfied(context);
    }

    private boolean isIPv6LoopbackAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("::1", port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                from("direct:serverKey")
                        .to(ipv6Endpoint(knownHostsServerKey, true))
                        .to("mock:result");
                from("direct:serverKeyAcceptingUnknownHosts")
                        .to(ipv6Endpoint(knownHostsServerKey, false))
                        .to("mock:result");
                from("direct:otherKey")
                        .to(ipv6Endpoint(knownHostsOtherKey, true))
                        .to("mock:result");
                from("direct:otherKeyAcceptingUnknownHosts")
                        .to(ipv6Endpoint(knownHostsOtherKey, false))
                        .to("mock:result");
            }
        };
    }

    private SshEndpoint ipv6Endpoint(String knownHosts, boolean failOnUnknownHost) {
        SshEndpoint endpoint = context.getEndpoint("ssh://smx:smx@localhost:" + port + "?timeout=3000&knownHostsResource="
                                                   + knownHosts + "&failOnUnknownHost=" + failOnUnknownHost,
                SshEndpoint.class);
        endpoint.setHost("::1");
        return endpoint;
    }
}
