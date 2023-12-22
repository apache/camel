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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.net.ServerSocket;

import com.salesforce.eventbus.protobuf.ReplayPreset;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.PubSubApiClient;
import org.apache.camel.component.salesforce.internal.pubsub.AuthErrorPubSubServer;
import org.apache.camel.component.salesforce.internal.pubsub.SendOneMessagePubSubServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PubSubApiTest {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubApiTest.class);

    @Test
    public void testReconnectOnErrorAfterReplayIdNonNull() throws Exception {
        final SalesforceSession session = mock(SalesforceSession.class);
        when(session.getAccessToken()).thenReturn("faketoken");
        when(session.getInstanceUrl()).thenReturn("https://myinstance");
        when(session.getOrgId()).thenReturn("00D123123123");

        final PubSubApiConsumer consumer = mock(PubSubApiConsumer.class);
        when(consumer.getTopic()).thenReturn("/event/FakeTopic");
        when(consumer.getBatchSize()).thenReturn(100);

        int port = getPort();
        LOG.debug("Starting server on port {}", port);
        final Server grpcServer = ServerBuilder.forPort(port)
                .addService(new SendOneMessagePubSubServer())
                .build();
        grpcServer.start();

        PubSubApiClient client = Mockito.spy(new PubSubApiClient(
                session, new SalesforceLoginConfig(), "localhost",
                port, 1000, 10000));
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.LATEST, null);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
        verify(client, times(1)).subscribe(consumer, ReplayPreset.LATEST, null);
        verify(client, times(1)).subscribe(consumer, ReplayPreset.CUSTOM, "MTIz");
    }

    @Test
    public void testReconnectOnErrorCustomWithInitialReplayId() throws Exception {
        final SalesforceSession session = mock(SalesforceSession.class);
        when(session.getAccessToken()).thenReturn("faketoken");
        when(session.getInstanceUrl()).thenReturn("https://myinstance");
        when(session.getOrgId()).thenReturn("00D123123123");

        final PubSubApiConsumer consumer = mock(PubSubApiConsumer.class);
        when(consumer.getTopic()).thenReturn("/event/FakeTopic");
        when(consumer.getBatchSize()).thenReturn(100);

        int port = getPort();
        LOG.debug("Starting server on port {}", port);
        final Server grpcServer = ServerBuilder.forPort(port)
                .addService(new AuthErrorPubSubServer())
                .build();
        grpcServer.start();

        PubSubApiClient client = Mockito.spy(new PubSubApiClient(
                session, new SalesforceLoginConfig(), "localhost",
                port, 1000, 10000));
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.CUSTOM, "initial");

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
        verify(client, times(2)).subscribe(consumer, ReplayPreset.CUSTOM, "initial");
    }

    @Test
    public void testReconnectOnErrorWithNullInitialReplayId() throws Exception {
        final SalesforceSession session = mock(SalesforceSession.class);
        when(session.getAccessToken()).thenReturn("faketoken");
        when(session.getInstanceUrl()).thenReturn("https://myinstance");
        when(session.getOrgId()).thenReturn("00D123123123");

        final PubSubApiConsumer consumer = mock(PubSubApiConsumer.class);
        when(consumer.getTopic()).thenReturn("/event/FakeTopic");
        when(consumer.getBatchSize()).thenReturn(100);

        int port = getPort();
        LOG.debug("Starting server on port {}", port);
        final Server grpcServer = ServerBuilder.forPort(port)
                .addService(new AuthErrorPubSubServer())
                .build();
        grpcServer.start();

        PubSubApiClient client = Mockito.spy(new PubSubApiClient(
                session, new SalesforceLoginConfig(), "localhost",
                port, 1000, 10000));
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.LATEST, null);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
        verify(client, times(2)).subscribe(consumer, ReplayPreset.LATEST, null);
    }

    @Test
    public void shouldAuthenticateAndSubscribeAfterAuthError() throws IOException {
        final SalesforceSession session = mock(SalesforceSession.class);
        when(session.getAccessToken()).thenReturn("faketoken");
        when(session.getInstanceUrl()).thenReturn("https://myinstance");
        when(session.getOrgId()).thenReturn("00D123123123");

        final PubSubApiConsumer consumer = mock(PubSubApiConsumer.class);
        when(consumer.getTopic()).thenReturn("/event/FakeTopic");
        when(consumer.getBatchSize()).thenReturn(100);

        int port = getPort();
        LOG.debug("Starting server on port {}", port);
        final Server grpcServer = ServerBuilder.forPort(port)
                .addService(new AuthErrorPubSubServer())
                .build();
        grpcServer.start();

        PubSubApiClient client = new PubSubApiClient(
                session, new SalesforceLoginConfig(), "localhost",
                port, 1000, 10000);
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.LATEST, null);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
    }

    private int getPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }
}
