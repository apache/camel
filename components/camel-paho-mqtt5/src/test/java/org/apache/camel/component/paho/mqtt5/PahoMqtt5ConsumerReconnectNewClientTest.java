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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Synchronization;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientException;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttPersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * manualAcksEnabled on a session that the broker keeps: a failed QoS 1 exchange makes the consumer reconnect with a new
 * client, as Paho 1.2.5 may never complete a connect of the same client while its callback thread still routes a
 * message (CAMEL-27043). The clients are mocks; PahoMqtt5ConsumerReconnectWhileRoutingTest runs the real client against
 * a broker.
 */
class PahoMqtt5ConsumerReconnectNewClientTest {

    private final Deque<Object> clients = new ArrayDeque<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private CamelContext context;
    private PahoMqtt5Consumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setQos(1);
        configuration.setManualAcksEnabled(true);
        configuration.setCleanStart(false);
        configuration.setSessionExpiryInterval(60);
        PahoMqtt5Endpoint endpoint = new PahoMqtt5Endpoint(
                "paho-mqtt5:test", "test", context.getComponent("paho-mqtt5", PahoMqtt5Component.class), configuration);
        consumer = new PahoMqtt5Consumer(endpoint, exchange -> {
        }) {
            @Override
            MqttClient createClient() throws MqttException {
                events.add("create");
                Object next = clients.removeFirst();
                if (next instanceof MqttException e) {
                    throw e;
                }
                if (next instanceof CountDownLatch[] latches) {
                    // [0] counted down once the reconnect creates the client, [1] lets it go on
                    latches[0].countDown();
                    awaitIgnoringInterrupts(latches[1]);
                    return (MqttClient) clients.removeFirst();
                }
                return (MqttClient) next;
            }
        };
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void failedExchangeClosesTheClientAndConnectsANewOne() throws Exception {
        MqttClient first = addClient("1");
        MqttClient second = addClient("2");
        consumer.start();

        fail(consumer.createExchange(message(7), "test"));

        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getClient() == second && events.contains("connect 2"));
        // the first client is closed before the second one is created (with file persistence both use the directory of
        // the client id), and the callback is set before the connect
        assertThat(events).containsExactly("create", "connect 1", "disconnect 1", "close 1", "create", "connect 2");
        verify(second).setManualAcks(true);
        verify(second).setCallback(any(MqttCallback.class));
        verify(first, never()).connectWithResult(any(MqttConnectionOptions.class));
        assertThat(consumer.getFailedMessageReconnects()).isOne();
    }

    @Test
    void exchangeOfTheReplacedClientIsNotAcknowledged() throws Exception {
        MqttClient first = addClient("1");
        MqttClient second = addClient("2");
        consumer.start();
        Exchange inRoute = consumer.createExchange(message(8), "test");

        fail(consumer.createExchange(message(7), "test"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getClient() == second && events.contains("connect 2"));
        complete(inRoute);

        // not on the closed client, and packet id 8 of the closed connection is not a message of the new client; the
        // broker sends the message again to the new client
        verify(first, never()).messageArrivedComplete(anyInt(), anyInt());
        verify(second, never()).messageArrivedComplete(anyInt(), anyInt());
        assertThat(consumer.getFailedMessageReconnects()).isOne();

        complete(consumer.createExchange(message(9), "test"));
        verify(second).messageArrivedComplete(9, 1);
        assertThat(consumer.getFailedMessageReconnects()).isZero();
    }

    @Test
    void failedExchangeOfTheReplacedClientDoesNotReconnect() throws Exception {
        addClient("1");
        MqttClient second = addClient("2");
        consumer.start();
        Exchange inRoute = consumer.createExchange(message(8), "test");

        fail(consumer.createExchange(message(7), "test"));
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getClient() == second && events.contains("connect 2"));
        fail(inRoute);

        // scheduleReconnect counts the reconnects in a row at once
        assertThat(consumer.getFailedMessageReconnects()).isOne();
        verify(second, never()).disconnect();
    }

    @Test
    void stopWhileTheNewClientIsCreatedClosesIt() throws Exception {
        addClient("1");
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        clients.add(new CountDownLatch[] { creating, release });
        MqttClient second = addClient("2");
        consumer.start();

        fail(consumer.createExchange(message(7), "test"));
        assertThat(creating.await(10, TimeUnit.SECONDS)).isTrue();
        consumer.stop();
        release.countDown();

        // the stopped consumer neither uses nor connects the new client
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(second).close(true));
        verify(second, never()).connectWithResult(any(MqttConnectionOptions.class));
        assertThat(consumer.getClient()).isNull();
    }

    @Test
    void failedCreateOfTheNewClientIsRetried() throws Exception {
        addClient("1");
        // what the constructor of a client throws when the file persistence directory cannot be written (Paho 1.2.5
        // ignores a lock that another client holds)
        clients.add(new MqttPersistenceException());
        MqttClient second = addClient("2");
        consumer.start();

        fail(consumer.createExchange(message(7), "test"));

        // the second reconnect waits 2 seconds
        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getClient() == second && events.contains("connect 2"));
        assertThat(consumer.getFailedMessageReconnects()).isEqualTo(2);
    }

    @Test
    void failedConnectOfTheNewClientIsRetriedWithAnotherClient() throws Exception {
        addClient("1");
        MqttClient second = addClient("2");
        doThrow(new MqttException(MqttClientException.REASON_CODE_SERVER_CONNECT_ERROR)).when(second)
                .connectWithResult(any(MqttConnectionOptions.class));
        MqttClient third = addClient("3");
        consumer.start();

        fail(consumer.createExchange(message(7), "test"));

        await().atMost(10, TimeUnit.SECONDS).until(() -> consumer.getClient() == third && events.contains("connect 3"));
        assertThat(events).containsSubsequence("close 1", "create", "close 2", "create", "connect 3");
        assertThat(consumer.getFailedMessageReconnects()).isEqualTo(2);
    }

    /**
     * A mock client that is connected after connect or connectWithResult and disconnected after disconnect, and records
     * those calls and close(true) in {@link #events}.
     */
    private MqttClient addClient(String name) throws Exception {
        MqttClient client = mock(MqttClient.class);
        AtomicBoolean connected = new AtomicBoolean();
        when(client.isConnected()).thenAnswer(invocation -> connected.get());
        doAnswer(invocation -> {
            events.add("connect " + name);
            connected.set(true);
            return null;
        }).when(client).connect(any(MqttConnectionOptions.class));
        IMqttToken sessionPresent = mock(IMqttToken.class);
        when(sessionPresent.getSessionPresent()).thenReturn(true);
        when(client.connectWithResult(any(MqttConnectionOptions.class))).thenAnswer(invocation -> {
            events.add("connect " + name);
            connected.set(true);
            return sessionPresent;
        });
        doAnswer(invocation -> {
            events.add("disconnect " + name);
            connected.set(false);
            return null;
        }).when(client).disconnect();
        doAnswer(invocation -> {
            events.add("close " + name);
            return null;
        }).when(client).close(true);
        clients.add(client);
        return client;
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        // the stop interrupts the reconnect thread (shutdownNow of its executor)
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (latch.getCount() > 0 && System.nanoTime() < deadline) {
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static MqttMessage message(int id) {
        MqttMessage message = new MqttMessage("body".getBytes());
        message.setId(id);
        message.setQos(1);
        return message;
    }

    private static void complete(Exchange exchange) {
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();
        completions.forEach(completion -> completion.onComplete(exchange));
    }

    private static void fail(Exchange exchange) {
        exchange.setException(new IllegalStateException("failed"));
        List<Synchronization> completions = exchange.getExchangeExtension().handoverCompletions();
        completions.forEach(completion -> completion.onFailure(exchange));
    }
}
