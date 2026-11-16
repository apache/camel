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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A broker that refuses a subscription answers with a SUBACK whose reason code is 0x80 or higher (MQTT 5, 3.9.3), for
 * example 0x87 Not authorized. Paho 1.2.5 returns from MqttClient.subscribe all the same (it puts the reason codes on
 * the token and throws nothing), and the broker then sends no message of the topic.
 */
class PahoMqtt5SubscribeRefusedTest extends CamelTestSupport {

    private static final String ROUTE_ID = "mqtt-consumer";

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    void routeDoesNotStartWhenTheBrokerRefusesTheSubscription() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        IMqttToken refused = token(MqttReturnCode.RETURN_CODE_NOT_AUTHORIZED);
        when(client.subscribe("test", 2)).thenReturn(refused);
        addRouteWithOwnedClient(client);

        Throwable thrown = catchThrowable(context::start);

        assertThat(thrown).rootCause().isInstanceOf(MqttException.class).hasMessageContaining("Not authorized");
        verify(client).disconnect();
        verify(client).close(true);
    }

    @Test
    void routeStartsWhenTheBrokerGrantsTheSubscription() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        IMqttToken granted = token(MqttReturnCode.RETURN_CODE_MAX_QOS_2);
        when(client.subscribe("test", 2)).thenReturn(granted);
        addRouteWithOwnedClient(client);

        context.start();

        assertThat(context.getRouteController().getRouteStatus(ROUTE_ID)).isEqualTo(ServiceStatus.Started);
    }

    @Test
    void refusedSubscriptionOnReconnectStopsTheRoute() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);
        IMqttToken granted = token(MqttReturnCode.RETURN_CODE_MAX_QOS_2);
        IMqttToken refused = token(MqttReturnCode.RETURN_CODE_NOT_AUTHORIZED);
        when(client.subscribe("test", 2)).thenReturn(granted, refused, refused);
        addRouteWithOwnedClient(client);
        context.start();
        ArgumentCaptor<MqttCallback> callback = ArgumentCaptor.forClass(MqttCallback.class);
        verify(client).setCallback(callback.capture());

        // the subscription is refused once the client reconnected: as for a subscribe that throws, the route is
        // restarted, and it does not start as long as the broker refuses the subscription
        callback.getValue().connectComplete(true, "tcp://localhost:1883");

        // the stop of the restart closes the client, and so does the start that fails
        verify(client, timeout(10_000).times(2)).close(true);
        verify(client, times(3)).subscribe("test", 2);
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(context.getRouteController().getRouteStatus(ROUTE_ID))
                        .isEqualTo(ServiceStatus.Stopped));
    }

    @Test
    void refusedSubscriptionAfterTheReconnectOfAFailedMessageStopsTheRoute() throws Exception {
        // a failed QoS 1 message on a session the broker keeps makes the consumer reconnect; the broker did not keep the
        // session, so the consumer subscribes again, and the broker refuses it. That connect created a session, so a
        // later reconnect would find it and not subscribe: the route is restarted, as for a failed resubscribe
        MqttClient client = mock(MqttClient.class);
        AtomicBoolean connected = new AtomicBoolean();
        when(client.isConnected()).thenAnswer(invocation -> connected.get());
        doAnswer(invocation -> {
            connected.set(true);
            return null;
        }).when(client).connect(any(MqttConnectionOptions.class));
        doAnswer(invocation -> {
            connected.set(false);
            return null;
        }).when(client).disconnect();
        IMqttToken noSession = mock(IMqttToken.class);
        when(client.connectWithResult(any(MqttConnectionOptions.class))).thenAnswer(invocation -> {
            connected.set(true);
            return noSession;
        });
        IMqttToken granted = token(MqttReturnCode.RETURN_CODE_MAX_QOS_1);
        IMqttToken refused = token(MqttReturnCode.RETURN_CODE_NOT_AUTHORIZED);
        when(client.subscribe("test", 1)).thenReturn(granted, refused, refused);
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setQos(1);
        configuration.setManualAcksEnabled(true);
        configuration.setCleanStart(false);
        configuration.setSessionExpiryInterval(60);
        addRouteWithOwnedClient(client, configuration);
        context.start();
        ArgumentCaptor<MqttCallback> callback = ArgumentCaptor.forClass(MqttCallback.class);
        verify(client).setCallback(callback.capture());
        MqttMessage message = new MqttMessage("fail".getBytes());
        message.setId(1);
        message.setQos(1);

        callback.getValue().messageArrived("test", message);

        // the reconnect subscribes again (refused), the stop of the restart closes the client, and so does the start
        // that fails
        verify(client, timeout(10_000)).connectWithResult(any(MqttConnectionOptions.class));
        verify(client, timeout(10_000).times(2)).close(true);
        verify(client, times(3)).subscribe("test", 1);
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(context.getRouteController().getRouteStatus(ROUTE_ID))
                        .isEqualTo(ServiceStatus.Stopped));
    }

    private static IMqttToken token(int reasonCode) {
        IMqttToken token = mock(IMqttToken.class);
        when(token.getReasonCodes()).thenReturn(new int[] { reasonCode });
        return token;
    }

    private void addRouteWithOwnedClient(MqttClient client) throws Exception {
        addRouteWithOwnedClient(client, new PahoMqtt5Configuration());
    }

    private void addRouteWithOwnedClient(MqttClient client, PahoMqtt5Configuration configuration) throws Exception {
        configuration.setBrokerUrl("tcp://localhost:1883");
        PahoMqtt5Component component = new PahoMqtt5Component(context) {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new PahoMqtt5Endpoint(uri, remaining, this, configuration.copy()) {
                    @Override
                    public Consumer createConsumer(Processor processor) throws Exception {
                        PahoMqtt5Consumer consumer = new PahoMqtt5Consumer(this, processor) {
                            @Override
                            MqttClient createClient() {
                                return client;
                            }
                        };
                        configureConsumer(consumer);
                        return consumer;
                    }
                };
            }
        };
        context.addComponent("paho-mqtt5-owned", component);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("paho-mqtt5-owned:test").id(ROUTE_ID)
                        .filter(body().convertToString().isEqualTo("fail"))
                        .throwException(new IllegalStateException("Simulated failure"))
                        .end()
                        .to("mock:result");
            }
        });
    }
}
