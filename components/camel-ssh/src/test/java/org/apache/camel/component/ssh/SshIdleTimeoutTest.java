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

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SshIdleTimeoutTest extends SshComponentTestSupport {

    private int delayedPort = AvailablePortFinder.getNextAvailable();
    private SshServer delayedSshd;

    @AfterEach
    public void stopDelayedServer() throws Exception {
        if (delayedSshd != null) {
            delayedSshd.stop(true);
            delayedSshd = null;
        }
    }

    @Test
    public void testProducerWithIdleTimeout() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshConstants.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshConstants.STDERR, "Error:test");

        template.sendBody("direct:sshWithIdleTimeout", msg);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testProducerIdleTimeoutAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&idleTimeout=60000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Optional<Duration> idleTimeout = CoreModuleProperties.IDLE_TIMEOUT.get(client);
        Assertions.assertTrue(idleTimeout.isPresent(), "Idle timeout should be set");
        Assertions.assertEquals(Duration.ofMillis(60000), idleTimeout.get());
    }

    @Test
    public void testConsumerIdleTimeoutAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&idleTimeout=60000", SshEndpoint.class);
        SshConsumer consumer = (SshConsumer) endpoint.createConsumer(x -> {
        });
        consumer.start();
        SshClient client = (SshClient) FieldUtils.readField(consumer, "client", true);

        Optional<Duration> idleTimeout = CoreModuleProperties.IDLE_TIMEOUT.get(client);
        Assertions.assertTrue(idleTimeout.isPresent(), "Idle timeout should be set");
        Assertions.assertEquals(Duration.ofMillis(60000), idleTimeout.get());
    }

    @Test
    public void testDefaultNoIdleTimeoutOnClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Duration idleTimeout = CoreModuleProperties.IDLE_TIMEOUT.getOrNull(client);
        Assertions.assertNull(idleTimeout, "Idle timeout should not be explicitly set by default");
    }

    private void startDelayedServer() throws Exception {
        delayedSshd = SshServer.setUpDefaultServer();
        delayedSshd.setPort(delayedPort);
        delayedSshd.setKeyPairProvider(new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem")));
        delayedSshd.setCommandFactory(new DelayedEchoCommandFactory(1000));
        delayedSshd.setPasswordAuthenticator((username, password, session) -> true);
        delayedSshd.setPublickeyAuthenticator((username, key, session) -> true);
        delayedSshd.start();
    }

    @Test
    public void testIdleTimeoutExpiresBeforeCommandCompletes() throws Exception {
        startDelayedServer();
        // Send the command using a producer with idleTimeout=500ms.
        // The client's idle timeout fires during the 3s command delay,
        // closing the session before the command completes.
        Exchange exchange = template.send(
                "direct:sshWithShortIdleTimeout",
                e -> e.getIn().setBody("test"));

        // The channel is closed before the command completes, so exit status is null.
        Object exitValue = exchange.getMessage().getHeader(SshConstants.EXIT_VALUE);
        Assertions.assertNull(exitValue,
                "Exit value should be null because the session was closed by idle timeout before the command completed");
    }

    @Test
    public void testIdleTimeoutLongerThanCommandDelay() throws Exception {
        startDelayedServer();
        // Send the command using a producer with idleTimeout=5000ms.
        // The command delay (3s) completes before the idle timeout fires.
        Exchange exchange = template.send(
                "direct:sshWithLongIdleTimeout",
                e -> e.getIn().setBody("test"));

        Object exitValue = exchange.getMessage().getHeader(SshConstants.EXIT_VALUE);
        Assertions.assertEquals(0, exitValue,
                "Exit value should be 0 because the idle timeout is longer than the command delay");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sshWithIdleTimeout")
                        .to("ssh://smx:smx@localhost:" + port + "?timeout=3000&idleTimeout=60000")
                        .to("mock:result");

                from("direct:sshWithShortIdleTimeout")
                        .to("ssh://smx:smx@localhost:" + delayedPort + "?timeout=5000&idleTimeout=500");
                from("direct:sshWithLongIdleTimeout")
                        .to("ssh://smx:smx@localhost:" + delayedPort + "?timeout=5000&idleTimeout=5000");
            }
        };
    }
}
