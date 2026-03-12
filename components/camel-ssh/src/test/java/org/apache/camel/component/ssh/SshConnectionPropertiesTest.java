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

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SshConnectionPropertiesTest extends SshComponentTestSupport {

    @Test
    public void testHeartbeatIntervalAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&heartbeatInterval=30000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Optional<Duration> heartbeat = CoreModuleProperties.HEARTBEAT_INTERVAL.get(client);
        Assertions.assertTrue(heartbeat.isPresent(), "Heartbeat interval should be set");
        Assertions.assertEquals(Duration.ofMillis(30000), heartbeat.get());
    }

    @Test
    public void testHeartbeatReplyMaxWaitAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&heartbeatReplyMaxWait=5", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Optional<Integer> maxWait = CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.get(client);
        Assertions.assertTrue(maxWait.isPresent(), "Heartbeat reply max wait should be set");
        Assertions.assertEquals(5, maxWait.get());
    }

    @Test
    public void testAuthTimeoutAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&authTimeout=15000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Optional<Duration> authTimeout = CoreModuleProperties.AUTH_TIMEOUT.get(client);
        Assertions.assertTrue(authTimeout.isPresent(), "Auth timeout should be set");
        Assertions.assertEquals(Duration.ofMillis(15000), authTimeout.get());
    }

    @Test
    public void testConnectTimeoutAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&connectTimeout=10000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Optional<Duration> connectTimeout = CoreModuleProperties.IO_CONNECT_TIMEOUT.get(client);
        Assertions.assertTrue(connectTimeout.isPresent(), "Connect timeout should be set");
        Assertions.assertEquals(Duration.ofMillis(10000), connectTimeout.get());
    }

    @Test
    public void testChannelOpenTimeoutAppliedToClient() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000&channelOpenTimeout=8000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Optional<Duration> channelOpenTimeout = CoreModuleProperties.CHANNEL_OPEN_TIMEOUT.get(client);
        Assertions.assertTrue(channelOpenTimeout.isPresent(), "Channel open timeout should be set");
        Assertions.assertEquals(Duration.ofMillis(8000), channelOpenTimeout.get());
    }

    @Test
    public void testDefaultsNotExplicitlySet() throws Exception {
        SshEndpoint endpoint = context.getEndpoint(
                "ssh://smx:smx@localhost:" + port + "?timeout=3000", SshEndpoint.class);
        SshProducer producer = (SshProducer) endpoint.createProducer();
        producer.start();
        SshClient client = (SshClient) FieldUtils.readField(producer, "client", true);

        Assertions.assertNull(CoreModuleProperties.HEARTBEAT_INTERVAL.getOrNull(client),
                "Heartbeat interval should not be explicitly set by default");
        Assertions.assertNull(CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.getOrNull(client),
                "Heartbeat reply max wait should not be explicitly set by default");
        Assertions.assertNull(CoreModuleProperties.AUTH_TIMEOUT.getOrNull(client),
                "Auth timeout should not be explicitly set by default");
        Assertions.assertNull(CoreModuleProperties.IO_CONNECT_TIMEOUT.getOrNull(client),
                "Connect timeout should not be explicitly set by default");
        Assertions.assertNull(CoreModuleProperties.CHANNEL_OPEN_TIMEOUT.getOrNull(client),
                "Channel open timeout should not be explicitly set by default");
    }
}
