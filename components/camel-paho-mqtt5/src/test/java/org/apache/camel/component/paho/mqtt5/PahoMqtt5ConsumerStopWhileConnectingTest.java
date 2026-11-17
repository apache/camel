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
package org.apache.camel.component.paho.mqtt5;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFactory;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientException;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stop while the own client of the consumer connects: here the automatic reconnect of Paho after the broker closed
 * the connection, which waits for a CONNACK that does not come. Paho 1.2.5 does not close a client while it connects,
 * also with force (32110, connect already in progress). The real Paho client runs against a minimal broker on a local
 * socket that answers the first CONNECT and SUBSCRIBE, closes the connection, and leaves the CONNECT of the reconnect
 * unanswered. The other tests use a mocked client that fails the first close as Paho does.
 */
class PahoMqtt5ConsumerStopWhileConnectingTest {

    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final CountDownLatch reconnecting = new CountDownLatch(1);
    private ServerSocket server;

    @AfterEach
    void closeBroker() throws IOException {
        for (Socket socket : sockets) {
            socket.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    @Timeout(60)
    void stopWhileTheClientReconnectsClosesTheClient() throws Exception {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread broker = new Thread(this::runBroker, "test-broker");
        broker.setDaemon(true);
        broker.start();
        PahoMqtt5Consumer consumer = createConsumer("tcp://127.0.0.1:" + server.getLocalPort(), null);
        consumer.doStart();
        MqttClient client = consumer.getClient();

        assertThat(reconnecting.await(20, TimeUnit.SECONDS)).as("the client reconnects").isTrue();
        assertThat(client.isConnected()).isFalse();

        assertThatNoException().isThrownBy(consumer::doStop);

        // the connect was aborted: the client closed its connection to the broker, and the client is closed
        Socket reconnect = sockets.get(1);
        reconnect.setSoTimeout(10_000);
        assertThat(reconnect.getInputStream().read()).isEqualTo(-1);
        MqttException closed = catchThrowableOfType(MqttException.class, client::connect);
        assertThat(closed.getReasonCode()).isEqualTo(MqttClientException.REASON_CODE_CLIENT_CLOSED);
    }

    @Test
    void stopWhileConnectingAbortsTheConnectBeforeClosing() throws Exception {
        // as Paho: the first close fails while the client connects, and passes once it is disconnected
        MqttClient client = mock(MqttClient.class);
        doThrow(new MqttException(MqttClientException.REASON_CODE_CONNECT_IN_PROGRESS)).doNothing().when(client).close(true);
        PahoMqtt5Consumer consumer = createConsumer("tcp://localhost:1883", client);
        consumer.doStart();

        assertThatNoException().isThrownBy(consumer::doStop);

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).close(true);
        inOrder.verify(client).disconnectForcibly(0, 0, false);
        inOrder.verify(client).close(true);
        verify(client, never()).disconnect();
    }

    @Test
    void stopRightAfterTheConnectCompletedDisconnectsBeforeClosing() throws Exception {
        // the connect completed after the stop found the client not connected: the close fails with 32100
        MqttClient client = mock(MqttClient.class);
        doThrow(new MqttException(MqttClientException.REASON_CODE_CLIENT_CONNECTED)).doNothing().when(client).close(true);
        PahoMqtt5Consumer consumer = createConsumer("tcp://localhost:1883", client);
        consumer.doStart();

        assertThatNoException().isThrownBy(consumer::doStop);

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).close(true);
        inOrder.verify(client).disconnect();
        inOrder.verify(client).close(true);
        verify(client, never()).disconnectForcibly(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void failedDisconnectRightAfterTheConnectCompletedAbortsBeforeClosing() throws Exception {
        // the client connected after the stop found it disconnected, and was disconnected again before the stop could
        MqttClient client = mock(MqttClient.class);
        doThrow(new MqttException(MqttClientException.REASON_CODE_CLIENT_CONNECTED)).doNothing().when(client).close(true);
        doThrow(new MqttException(MqttClientException.REASON_CODE_CLIENT_ALREADY_DISCONNECTED)).when(client).disconnect();
        PahoMqtt5Consumer consumer = createConsumer("tcp://localhost:1883", client);
        consumer.doStart();

        assertThatNoException().isThrownBy(consumer::doStop);

        InOrder inOrder = inOrder(client);
        inOrder.verify(client).close(true);
        inOrder.verify(client).disconnect();
        inOrder.verify(client).disconnectForcibly(0, 0, false);
        inOrder.verify(client).close(true);
    }

    @Test
    void otherCloseFailureIsThrown() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttException closeException = new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
        doThrow(closeException).when(client).close(true);
        PahoMqtt5Consumer consumer = createConsumer("tcp://localhost:1883", client);
        consumer.doStart();

        MqttException thrown = catchThrowableOfType(MqttException.class, consumer::doStop);

        assertThat(thrown).isSameAs(closeException);
        verify(client, never()).disconnectForcibly(anyLong(), anyLong(), anyBoolean());
    }

    private void runBroker() {
        try {
            Socket first = server.accept();
            sockets.add(first);
            InputStream in = first.getInputStream();
            OutputStream out = first.getOutputStream();
            readPacket(in);
            // CONNACK: no session present, reason code 0 (success), no properties
            out.write(new byte[] { 0x20, 3, 0, 0, 0 });
            out.flush();
            byte[] subscribe = readPacket(in);
            // SUBACK: the packet identifier of the SUBSCRIBE, no properties, reason code 2 (granted QoS 2)
            out.write(new byte[] { (byte) 0x90, 4, subscribe[0], subscribe[1], 0, 2 });
            out.flush();
            // the broker closes the connection: Paho reconnects on its own (automaticReconnect is on by default)
            first.close();

            Socket second = server.accept();
            sockets.add(second);
            readPacket(second.getInputStream());
            // the CONNECT of the reconnect is never answered, so the client stays connecting
            reconnecting.countDown();
        } catch (IOException e) {
            // the test closed the broker
        }
    }

    /**
     * Reads one MQTT control packet and returns its variable header and payload.
     */
    private static byte[] readPacket(InputStream in) throws IOException {
        if (in.read() < 0) {
            throw new EOFException();
        }
        int length = 0;
        int multiplier = 1;
        int b;
        do {
            b = in.read();
            if (b < 0) {
                throw new EOFException();
            }
            length += (b & 0x7F) * multiplier;
            multiplier *= 128;
        } while ((b & 0x80) != 0);
        return in.readNBytes(length);
    }

    /**
     * The consumer with its own client: the given mock, or the real Paho client when it is null.
     */
    private static PahoMqtt5Consumer createConsumer(String brokerUrl, MqttClient createdClient) {
        CamelContext context = mock(CamelContext.class);
        ExtendedCamelContext extension = mock(ExtendedCamelContext.class);
        ExchangeFactory exchangeFactory = mock(ExchangeFactory.class);
        when(context.getCamelContextExtension()).thenReturn(extension);
        when(extension.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setBrokerUrl(brokerUrl);
        PahoMqtt5Endpoint endpoint = new PahoMqtt5Endpoint(
                "paho-mqtt5:test", "test", new PahoMqtt5Component(context), configuration);
        if (createdClient == null) {
            return new PahoMqtt5Consumer(endpoint, mock(Processor.class));
        }
        return new PahoMqtt5Consumer(endpoint, mock(Processor.class)) {
            @Override
            MqttClient createClient() {
                return createdClient;
            }
        };
    }
}
