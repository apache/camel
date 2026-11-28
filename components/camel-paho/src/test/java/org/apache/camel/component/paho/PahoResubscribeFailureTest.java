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
package org.apache.camel.component.paho;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PahoResubscribeFailureTest extends CamelTestSupport {

    private static final String ROUTE_ID = "mqtt-consumer";

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
            }
        };
    }

    @Test
    void successfulResubscribeOnReconnectShouldKeepRouteStarted() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttCallbackExtended callback = startRouteWithExternalClient(client);

        callback.connectComplete(true, "tcp://localhost:1883");

        verify(client, times(2)).subscribe("test", 2);
        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void resubscribeFailureWithOwnedClientShouldRestartRouteAndReplaceOwnedClient() throws Exception {
        MqttClient failedClient = connectedClient();
        MqttClient recoveredClient = connectedClient();
        AtomicInteger createdClients = new AtomicInteger();
        MqttCallbackExtended callback = startRouteWithOwnedClients(createdClients, failedClient, recoveredClient);
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(failedClient).subscribe(anyString(), anyInt());

        callback.connectComplete(true, "tcp://localhost:1883");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
            assertThat(createdClients).hasValue(2);
            verify(failedClient).close(true);
            verify(recoveredClient).connect(any(MqttConnectOptions.class));
            verify(recoveredClient).subscribe("test", 2);
            verify(recoveredClient, never()).close(true);
        });

        callback.connectComplete(true, "tcp://localhost:1883");

        assertThat(createdClients).hasValue(2);
        verify(failedClient, times(2)).subscribe("test", 2);
    }

    @Test
    void resubscribeFailureWithExternalClientShouldNotRestartOrCloseClient() throws Exception {
        MqttClient client = connectedClient();
        MqttCallbackExtended callback = startRouteWithExternalClient(client);
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(client).subscribe(anyString(), anyInt());

        callback.connectComplete(true, "tcp://localhost:1883");

        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
        verify(client, times(2)).subscribe("test", 2);
        verify(client, never()).connect(any(MqttConnectOptions.class));
        verify(client, never()).close(true);
    }

    @Test
    void concurrentReconnectFailuresShouldTriggerOnlyOneRestart() throws Exception {
        MqttClient failedClient = connectedClient();
        MqttClient recoveredClient = connectedClient();
        AtomicInteger createdClients = new AtomicInteger();
        MqttCallbackExtended callback = startRouteWithOwnedClients(createdClients, failedClient, recoveredClient);
        CyclicBarrier subscribeBarrier = new CyclicBarrier(2);
        CountDownLatch closeEntered = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        doAnswer(invocation -> {
            subscribeBarrier.await(5, TimeUnit.SECONDS);
            throw new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION);
        }).when(failedClient).subscribe(anyString(), anyInt());
        doAnswer(invocation -> {
            closeEntered.countDown();
            assertThat(releaseClose.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(failedClient).close(true);

        CompletableFuture<Void> first = CompletableFuture.runAsync(
                () -> callback.connectComplete(true, "tcp://localhost:1883"));
        CompletableFuture<Void> second = CompletableFuture.runAsync(
                () -> callback.connectComplete(true, "tcp://localhost:1883"));

        assertThat(closeEntered.await(5, TimeUnit.SECONDS)).isTrue();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        releaseClose.countDown();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
            assertThat(createdClients).hasValue(2);
            verify(failedClient).close(true);
            verify(recoveredClient).connect(any(MqttConnectOptions.class));
        });
    }

    @Test
    void reconnectCallbackAfterShutdownShouldNotResubscribeOrRestart() throws Exception {
        MqttClient client = connectedClient();
        AtomicInteger createdClients = new AtomicInteger();
        MqttCallbackExtended callback = startRouteWithOwnedClients(createdClients, client);

        context.getRouteController().stopRoute(ROUTE_ID);
        callback.connectComplete(true, "tcp://localhost:1883");

        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Stopped);
        assertThat(createdClients).hasValue(1);
        verify(client, times(1)).subscribe("test", 2);
        verify(client).close(true);
    }

    @Test
    void initialConnectShouldNotResubscribe() throws Exception {
        MqttClient client = mock(MqttClient.class);
        MqttCallbackExtended callback = startRouteWithExternalClient(client);

        callback.connectComplete(false, "tcp://localhost:1883");

        verify(client, times(1)).subscribe("test", 2);
        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
    }

    private MqttCallbackExtended startRouteWithExternalClient(MqttClient client) throws Exception {
        PahoEndpoint endpoint = context.getEndpoint(
                "paho:test?brokerUrl=tcp://localhost:1883", PahoEndpoint.class);
        endpoint.setClient(client);
        context.addRoutes(createRoute("paho:test?brokerUrl=tcp://localhost:1883"));
        context.start();
        return captureCallback(client);
    }

    private MqttCallbackExtended startRouteWithOwnedClients(
            AtomicInteger createdClients, MqttClient... clients)
            throws Exception {
        PahoConfiguration configuration = new PahoConfiguration();
        configuration.setAutomaticReconnect(true);
        configuration.setBrokerUrl("tcp://localhost:1883");
        Deque<MqttClient> availableClients = new ArrayDeque<>(Arrays.asList(clients));
        PahoComponent component = new PahoComponent(context) {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                PahoEndpoint endpoint = new PahoEndpoint(uri, remaining, this, configuration.copy()) {
                    @Override
                    public Consumer createConsumer(Processor processor) throws Exception {
                        PahoConsumer consumer = new PahoConsumer(this, processor) {
                            @Override
                            MqttClient createClient() {
                                createdClients.incrementAndGet();
                                return availableClients.removeFirst();
                            }
                        };
                        configureConsumer(consumer);
                        return consumer;
                    }
                };
                return endpoint;
            }
        };
        context.addComponent("paho-owned", component);
        context.addRoutes(createRoute("paho-owned:test"));
        context.start();
        return captureCallback(clients[0]);
    }

    private RouteBuilder createRoute(String uri) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).id(ROUTE_ID).to("mock:result");
            }
        };
    }

    private MqttCallbackExtended captureCallback(MqttClient client) throws Exception {
        ArgumentCaptor<MqttCallbackExtended> callbackCaptor = ArgumentCaptor.forClass(MqttCallbackExtended.class);
        verify(client).setCallback(callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    private static MqttClient connectedClient() {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        return client;
    }
}
