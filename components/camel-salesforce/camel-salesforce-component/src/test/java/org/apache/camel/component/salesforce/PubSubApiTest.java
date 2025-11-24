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
import java.util.Base64;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.PubSubApiClient;
import org.apache.camel.component.salesforce.internal.pubsub.AuthErrorPubSubServer;
import org.apache.camel.component.salesforce.internal.pubsub.SendInvalidReplayIdErrorPubSubServer;
import org.apache.camel.component.salesforce.internal.pubsub.SendOneMessagePubSubServer;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
                port, 1000, 10000, true));
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.LATEST, null, true);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
        verify(client, timeout(5000).times(1)).subscribe(consumer, ReplayPreset.LATEST, null, true);
        verify(client, timeout(5000).times(1)).subscribe(consumer, ReplayPreset.CUSTOM, "MTIz", true);
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
                port, 1000, 10000, true));
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.CUSTOM, "initial", true);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
        verify(client, timeout(5000).times(2)).subscribe(consumer, ReplayPreset.CUSTOM, "initial", true);
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
                port, 1000, 10000, true));
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.LATEST, null, true);

        Thread.sleep(1000);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
        verify(client, timeout(5000).times(2)).subscribe(consumer, ReplayPreset.LATEST, null, true);
    }

    @Test
    public void testFallbackToLatestReplayIdWhenReplayIdIsCorrupted() throws Exception {
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
                .addService(new SendInvalidReplayIdErrorPubSubServer(1))
                .build();
        grpcServer.start();

        PubSubApiClient client = Mockito.spy(new PubSubApiClient(
                session, new SalesforceLoginConfig(), "localhost",
                port, 1000, 10000, true));
        client.setUsePlainTextConnection(true);
        client.start();
        String replayId = encodeReplayId("123");
        client.subscribe(consumer, ReplayPreset.CUSTOM, replayId, true);

        Thread.sleep(1000);

        InOrder inOrder = Mockito.inOrder(client);
        inOrder.verify(client, timeout(5000)).subscribe(consumer, ReplayPreset.CUSTOM, replayId, true);
        inOrder.verify(client, timeout(5000)).subscribe(consumer, ReplayPreset.LATEST, null, true);
    }

    @Test
    public void testInvokesExceptionHandlerWhenReplayIdIsCorruptedAndFallbackToLatestReplayIdIsDisabled() throws Exception {
        final SalesforceSession session = mock(SalesforceSession.class);
        when(session.getAccessToken()).thenReturn("faketoken");
        when(session.getInstanceUrl()).thenReturn("https://myinstance");
        when(session.getOrgId()).thenReturn("00D123123123");

        final ExceptionHandler exceptionHandler = mock(ExceptionHandler.class);
        final PubSubApiConsumer consumer = mock(PubSubApiConsumer.class);
        when(consumer.getTopic()).thenReturn("/event/FakeTopic");
        when(consumer.getBatchSize()).thenReturn(100);
        when(consumer.getExceptionHandler()).thenReturn(exceptionHandler);

        int port = getPort();
        LOG.debug("Starting server on port {}", port);
        final Server grpcServer = ServerBuilder.forPort(port)
                .addService(new SendInvalidReplayIdErrorPubSubServer(3))
                .build();
        grpcServer.start();

        PubSubApiClient client = Mockito.spy(new PubSubApiClient(
                session, new SalesforceLoginConfig(), "localhost",
                port, 1000, 10000, true));
        client.setUsePlainTextConnection(true);
        client.start();
        final String replayId = encodeReplayId("123");
        client.subscribe(consumer, ReplayPreset.CUSTOM, replayId, false);

        Thread.sleep(1000);

        InOrder inOrder = Mockito.inOrder(client);
        inOrder.verify(client, timeout(5000).times(3)).subscribe(consumer, ReplayPreset.CUSTOM, replayId, false);
        inOrder.verify(client, never()).subscribe(consumer, ReplayPreset.LATEST, null, false);

        ArgumentCaptor<InvalidReplayIdException> captor = ArgumentCaptor.forClass(InvalidReplayIdException.class);
        verify(exceptionHandler, timeout(5000).times(3)).handleException(captor.capture());
        for (InvalidReplayIdException exception : captor.getAllValues()) {
            Assertions.assertEquals(replayId, exception.getReplayId());
        }
    }

    private String encodeReplayId(String replayId) {
        return Base64.getEncoder().encodeToString(ByteString.copyFromUtf8(replayId).toByteArray());
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
                port, 1000, 10000, true);
        client.setUsePlainTextConnection(true);
        client.start();
        client.subscribe(consumer, ReplayPreset.LATEST, null, true);

        verify(session, timeout(5000)).attemptLoginUntilSuccessful(anyLong(), anyLong());
    }

    private int getPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }
}
