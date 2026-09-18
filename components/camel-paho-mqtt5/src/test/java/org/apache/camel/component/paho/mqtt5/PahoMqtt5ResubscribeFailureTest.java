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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PahoMqtt5ResubscribeFailureTest extends CamelTestSupport {

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

    private MqttCallback startRouteWithExternalClient(MqttClient mockClient) throws Exception {
        PahoMqtt5Endpoint endpoint = context.getEndpoint(
                "paho-mqtt5:test?brokerUrl=tcp://localhost:1883", PahoMqtt5Endpoint.class);
        endpoint.setClient(mockClient);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("paho-mqtt5:test?brokerUrl=tcp://localhost:1883")
                        .id(ROUTE_ID)
                        .to("mock:result");
            }
        });

        context.start();

        return captureCallback(mockClient);
    }

    private MqttCallback startRouteWithOwnedClient(MqttClient mockClient) throws Exception {
        PahoMqtt5Configuration config = new PahoMqtt5Configuration();
        config.setBrokerUrl("tcp://localhost:1883");

        PahoMqtt5Component component = new PahoMqtt5Component(context) {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                PahoMqtt5Endpoint endpoint = new PahoMqtt5Endpoint(uri, remaining, this, config.copy()) {
                    @Override
                    public Consumer createConsumer(Processor processor) throws Exception {
                        PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(this, processor) {
                            @Override
                            MqttClient createClient() {
                                return mockClient;
                            }
                        };
                        configureConsumer(consumer);
                        return consumer;
                    }
                };
                return endpoint;
            }
        };
        context.addComponent("paho-mqtt5-owned", component);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("paho-mqtt5-owned:test")
                        .id(ROUTE_ID)
                        .to("mock:result");
            }
        });

        context.start();

        return captureCallback(mockClient);
    }

    private MqttCallback captureCallback(MqttClient mockClient) throws Exception {
        ArgumentCaptor<MqttCallback> callbackCaptor = ArgumentCaptor.forClass(MqttCallback.class);
        verify(mockClient).setCallback(callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    @Test
    void resubscribeFailureWithExternalClientShouldNotRestartRoute() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        MqttCallback callback = startRouteWithExternalClient(mockClient);

        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);

        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockClient).subscribe(anyString(), anyInt());

        callback.connectComplete(true, "tcp://localhost:1883");

        await().during(2, TimeUnit.SECONDS)
                .atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(context.getRouteController().getRouteStatus(ROUTE_ID))
                        .isEqualTo(ServiceStatus.Started));
    }

    @Test
    void resubscribeFailureWithOwnedClientShouldStopRoute() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        MqttCallback callback = startRouteWithOwnedClient(mockClient);

        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockClient).subscribe(anyString(), anyInt());

        callback.connectComplete(true, "tcp://localhost:1883");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(context.getRouteController().getRouteStatus(ROUTE_ID))
                        .isEqualTo(ServiceStatus.Stopped));
    }

    @Test
    void successfulResubscribeOnReconnectShouldKeepRouteStarted() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        MqttCallback callback = startRouteWithExternalClient(mockClient);

        callback.connectComplete(true, "tcp://localhost:1883");

        verify(mockClient, times(2)).subscribe("test", 2);
        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void duplicateReconnectsShouldNotCauseConcurrentRestarts() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        MqttCallback callback = startRouteWithOwnedClient(mockClient);

        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockClient).subscribe(anyString(), anyInt());

        callback.connectComplete(true, "tcp://localhost:1883");
        callback.connectComplete(true, "tcp://localhost:1883");

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(context.getRouteController().getRouteStatus(ROUTE_ID))
                        .isEqualTo(ServiceStatus.Stopped));
    }

    @Test
    void initialConnectShouldNotResubscribe() throws Exception {
        MqttClient mockClient = mock(MqttClient.class);
        when(mockClient.isConnected()).thenReturn(true);

        MqttCallback callback = startRouteWithExternalClient(mockClient);

        callback.connectComplete(false, "tcp://localhost:1883");

        verify(mockClient, times(1)).subscribe("test", 2);
        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
    }
}
