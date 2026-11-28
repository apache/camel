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
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.kex.AbstractDH;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.DHFactory;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSessionFactory;
import org.apache.sshd.server.forward.DirectTcpipFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A misconfigured endpoint fails with a message that names the problem, instead of a NullPointerException, and the
 * options that had no effect apply.
 */
public class SshConfigurationErrorsTest extends SshComponentTestSupport {

    @Test
    public void testRefusedChannelSaysSo() throws Exception {
        // a server that accepts the session but refuses the session channel
        SshServer refusing = SshServer.setUpDefaultServer();
        refusing.setPort(0);
        refusing.setKeyPairProvider(new FileKeyPairProvider(Paths.get(getHostKey())));
        refusing.setPasswordAuthenticator((username, password, session) -> true);
        // only forwarding channels: the session channel that runs the command is refused
        refusing.setChannelFactories(List.of(DirectTcpipFactory.INSTANCE));
        refusing.start();
        try {
            String uri = "ssh://smx:smx@localhost:" + refusing.getPort() + "?timeout=30000";
            CamelExecutionException e
                    = assertThrows(CamelExecutionException.class, () -> template.requestBody(uri, "test"));

            Throwable cause = causeStartingWith(e, "The server refused to open the exec channel to localhost:");
            assertTrue(cause.getCause() != null, "the reason from the server is the cause");
        } finally {
            refusing.stop(true);
        }
    }

    @Test
    public void testChannelThatDoesNotOpenInTimeFailsWithChannelOpenTimeout() throws Exception {
        // a server that does not open the session channel until the test releases it
        CountDownLatch release = new CountDownLatch(1);
        SshServer slow = SshServer.setUpDefaultServer();
        slow.setPort(0);
        slow.setKeyPairProvider(new FileKeyPairProvider(Paths.get(getHostKey())));
        slow.setPasswordAuthenticator((username, password, session) -> true);
        slow.setCommandFactory(new TestEchoCommandFactory());
        slow.setChannelFactories(List.of(new ChannelFactory() {
            @Override
            public String getName() {
                return ChannelSessionFactory.INSTANCE.getName();
            }

            @Override
            public Channel createChannel(Session session) throws IOException {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ChannelSessionFactory.INSTANCE.createChannel(session);
            }
        }));
        slow.start();
        try {
            // channelOpenTimeout, not timeout, limits the wait for the channel
            String uri = "ssh://smx:smx@localhost:" + slow.getPort() + "?timeout=30000&channelOpenTimeout=500";
            CamelExecutionException e
                    = assertThrows(CamelExecutionException.class, () -> template.requestBody(uri, "test"));

            Throwable cause = causeStartingWith(e, "Failed to open the exec channel to localhost:");
            assertTrue(cause.getMessage().endsWith(" within timeout 500ms"), cause.getMessage());
        } finally {
            release.countDown();
            slow.stop(true);
        }
    }

    @Test
    public void testKeyTypeWithoutKeyPairNamesTheKeyType() {
        String uri = "ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/rsa.pem&keyType=ssh-ed25519";
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> template.requestBody(uri, "test"));

        causeStartingWith(e, "No key pair of type ssh-ed25519 found in certResource file:src/test/resources/rsa.pem");
    }

    @Test
    public void testUnreadableCertResourceNamesTheResource() {
        String uri = "ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/no-such-key.pem";
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> template.requestBody(uri, "test"));

        causeStartingWith(e, "No key pair found in certResource file:src/test/resources/no-such-key.pem");
    }

    @Test
    public void testKeySourceDoesNotPrintAnInlineKey() {
        assertEquals("certResource file:/keys/id_rsa", SshHelper.describeKeySource("file:/keys/id_rsa"));
        assertEquals("certResource (base64)", SshHelper.describeKeySource("base64:LS0tLS1CRUdJTiBQUklWQVRF"));
        assertEquals("certResource (mem)", SshHelper.describeKeySource("mem:-----BEGIN PRIVATE KEY-----"));
        assertEquals("keyPairProvider", SshHelper.describeKeySource(null));
    }

    @Test
    public void testUnsupportedChannelTypeFailsToStart() {
        // the endpoint starts when it is resolved
        ResolveEndpointFailedException e = assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("ssh://smx:smx@localhost:" + port + "?channelType=subsystem"));

        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("Unsupported channelType subsystem: set channelType to exec or shell", e.getCause().getMessage());
    }

    @Test
    public void testConsumerWithoutPollCommandFailsToStart() throws Exception {
        SshEndpoint endpoint = context.getEndpoint("ssh://smx:smx@localhost:" + port, SshEndpoint.class);
        SshConsumer consumer = (SshConsumer) endpoint.createConsumer(exchange -> {
        });

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, consumer::start);
        assertEquals("The consumer needs the pollCommand option: set it to the command to run", e.getMessage());
    }

    @Test
    public void testIdleTimeoutDefaultAndZero() throws Exception {
        // the default keeps the 10 minutes of Apache MINA SSHD, and 0 turns the idle timeout off
        assertEquals(Duration.ofMinutes(10), idleTimeoutOf("ssh://smx:smx@localhost:" + port));
        assertEquals(Duration.ZERO, idleTimeoutOf("ssh://smx:smx@localhost:" + port + "?idleTimeout=0"));
        assertEquals(Duration.ZERO, idleTimeoutOf("ssh://smx:smx@localhost:" + port + "?idleTimeout=-1"));
    }

    @Test
    public void testKexIncludesRegisteredExtensions() {
        DHFactory extension = new RenamedDHFactory("test-kex@camel.apache.org", BuiltinDHFactories.ecdhp256);
        BuiltinDHFactories.registerExtension(extension);
        try {
            SshClient client = SshClient.setUpDefaultClient();
            SshUtils.configureKexAlgorithms("test-kex@camel.apache.org,ecdh-sha2-nistp384", client);

            List<String> names = client.getKeyExchangeFactories().stream().map(KeyExchangeFactory::getName).toList();
            assertEquals(List.of("test-kex@camel.apache.org", "ecdh-sha2-nistp384"), names);
        } finally {
            BuiltinDHFactories.unregisterExtension(extension.getName());
        }
    }

    private static Throwable causeStartingWith(Throwable e, String prefix) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().startsWith(prefix)) {
                return t;
            }
        }
        throw new AssertionError("No cause starting with '" + prefix + "' in " + e, e);
    }

    private Duration idleTimeoutOf(String uri) throws Exception {
        SshEndpoint endpoint = context.getEndpoint(uri, SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        try {
            SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);
            return CoreModuleProperties.IDLE_TIMEOUT.getRequired(client);
        } finally {
            producer.stop();
        }
    }

    /**
     * A key exchange registered under another name, as a provider of an extension would register it.
     */
    private static final class RenamedDHFactory implements DHFactory {
        private final String name;
        private final DHFactory delegate;

        RenamedDHFactory(String name, DHFactory delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isSupported() {
            return delegate.isSupported();
        }

        @Override
        public boolean isGroupExchange() {
            return delegate.isGroupExchange();
        }

        @Override
        public AbstractDH create(Object... params) throws Exception {
            return delegate.create(params);
        }

        @Override
        public String toString() {
            return NamedResource.class.getSimpleName() + "[" + name + "]";
        }
    }
}
