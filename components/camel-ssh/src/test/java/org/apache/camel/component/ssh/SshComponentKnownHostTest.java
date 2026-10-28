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
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SshComponentKnownHostTest extends SshComponentTestSupport {

    private String knownHostsValid;
    private String knownHostsOtherHost;
    private String knownHostsSeveralKeys;
    private String knownHostsOtherKey;

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();
        String serverKey = serverPublicKey();
        String serverEntry = "[localhost]:" + port + " " + serverKey;
        knownHostsValid = writeKnownHosts("known_hosts_valid", serverEntry);
        knownHostsOtherHost = writeKnownHosts("known_hosts_other_host", "old-localhost " + serverKey);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String otherKey = PublicKeyEntry.toString(generator.generateKeyPair().getPublic());
        knownHostsSeveralKeys
                = writeKnownHosts("known_hosts_several_keys", "[localhost]:" + port + " " + otherKey, serverEntry);
        knownHostsOtherKey = writeKnownHosts("known_hosts_other_key", "[localhost]:" + port + " " + otherKey);
    }

    @Test
    public void testProducerWithValidFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshConstants.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshConstants.STDERR, "Error:test");

        template.sendBody("direct:ssh", msg);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testProducerWithInvalidFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMessageCount(0);

        template.sendBody("direct:sshInvalid", msg);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testProducerWithInvalidFileWarnOnly() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshConstants.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshConstants.STDERR, "Error:test");

        template.sendBody("direct:sshInvalidWarnOnly", msg);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testProducerWithKeyListedForAnotherHost() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:sshOtherHost", "test");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testProducerWithSeveralKeysForTheServer() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:sshSeveralKeys", msg);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void testProducerWithAnAliasInTheHostConfiguration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("test");

        template.sendBody("direct:aliasServerKey", "test");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(authenticatedUsers.contains("aliasServerKey"));
    }

    @Test
    void testProducerWithAnAliasInTheHostConfigurationAndAnotherKey() throws Exception {
        getMockEndpoint("mock:password").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:aliasOtherKey", "test");

        MockEndpoint.assertIsSatisfied(context);
        assertFalse(authenticatedUsers.contains("aliasOtherKey"));
    }

    @Test
    void testProducerWithAFailingHostConfiguration() throws Exception {
        assertNotConnected("brokenAlias");
    }

    @Test
    void testProducerWithAFailingHostConfigurationAcceptingUnknownHosts() throws Exception {
        assertNotConnected("brokenAliasAcceptingUnknownHosts");
    }

    private void assertNotConnected(String route) throws Exception {
        getMockEndpoint("mock:password").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:" + route, "test");

        MockEndpoint.assertIsSatisfied(context);
        assertFalse(authenticatedUsers.contains(route));
    }

    @Test
    public void testPollingConsumerWithValidKnownHostFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("test");
        mock.expectedHeaderReceived(SshConstants.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshConstants.STDERR, "Error:test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPollingConsumerWithInvalidKnownHostFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultInvalid");
        mock.expectedMessageCount(0);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPollingConsumerWithInvalidKnownHostFileWarnOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultInvalidWarnOnly");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("test");
        mock.expectedHeaderReceived(SshConstants.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshConstants.STDERR, "Error:test");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                from("ssh://smx:smx@localhost:" + port
                     + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=" + knownHostsValid
                     + "&failOnUnknownHost=true")
                        .to("mock:result");

                from("ssh://smx:smx@localhost:" + port
                     + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=classpath:known_hosts_invalid&failOnUnknownHost=true")
                        .to("mock:resultInvalid");

                from("ssh://smx:smx@localhost:" + port
                     + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=classpath:known_hosts_invalid")
                        .to("mock:resultInvalidWarnOnly");

                from("direct:ssh")
                        .to("ssh://smx:smx@localhost:" + port
                            + "?timeout=3000&knownHostsResource=" + knownHostsValid + "&failOnUnknownHost=true")
                        .to("mock:password");

                from("direct:sshOtherHost")
                        .to("ssh://smx:smx@localhost:" + port
                            + "?timeout=3000&knownHostsResource=" + knownHostsOtherHost + "&failOnUnknownHost=true")
                        .to("mock:password");

                // the host configuration of the client, as ~/.ssh/config would give it: Host db-alias, HostName localhost
                from("direct:aliasServerKey").to(aliasEndpoint("aliasServerKey", knownHostsValid, true))
                        .to("mock:password");
                from("direct:aliasOtherKey").to(aliasEndpoint("aliasOtherKey", knownHostsOtherKey, false))
                        .to("mock:password");

                // a host configuration that cannot resolve the host: the producer must not connect anywhere
                from("direct:brokenAlias").to(brokenAliasEndpoint("brokenAlias", true)).to("mock:password");
                from("direct:brokenAliasAcceptingUnknownHosts")
                        .to(brokenAliasEndpoint("brokenAliasAcceptingUnknownHosts", false)).to("mock:password");

                from("direct:sshSeveralKeys")
                        .to("ssh://smx:smx@localhost:" + port
                            + "?timeout=3000&knownHostsResource=" + knownHostsSeveralKeys + "&failOnUnknownHost=true")
                        .to("mock:password");

                from("direct:sshInvalid").to("ssh://smx:smx@localhost:" + port
                                             + "?timeout=3000&knownHostsResource=classpath:known_hosts_invalid&failOnUnknownHost=true")
                        .to("mock:password");

                from("direct:sshInvalidWarnOnly").to("ssh://smx:smx@localhost:" + port
                                                     + "?timeout=3000&knownHostsResource=classpath:known_hosts_invalid")
                        .to("mock:password");
            }
        };
    }

    private SshEndpoint aliasEndpoint(String user, String knownHosts, boolean failOnUnknownHost) {
        SshEndpoint endpoint = context.getEndpoint("ssh://" + user + ":secret@db-alias:" + port
                                                   + "?timeout=3000&knownHostsResource=" + knownHosts
                                                   + "&failOnUnknownHost=" + failOnUnknownHost,
                SshEndpoint.class);
        endpoint.getConfiguration().setClientBuilder(ClientBuilder.builder().hostConfigEntryResolver(
                (host, hostPort, localAddress, username, proxyJump, attributes) -> "db-alias".equals(host)
                        ? new HostConfigEntry(host, "localhost", hostPort, username) : null));
        return endpoint;
    }

    private SshEndpoint brokenAliasEndpoint(String user, boolean failOnUnknownHost) {
        SshEndpoint endpoint = context.getEndpoint("ssh://" + user + ":secret@broken-alias:" + port
                                                   + "?timeout=3000&knownHostsResource=" + knownHostsValid
                                                   + "&failOnUnknownHost=" + failOnUnknownHost,
                SshEndpoint.class);
        endpoint.getConfiguration().setClientBuilder(ClientBuilder.builder().hostConfigEntryResolver(
                (host, hostPort, localAddress, username, proxyJump, attributes) -> {
                    throw new IOException("Cannot read the host configuration");
                }));
        return endpoint;
    }
}
