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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * A consumer that connects to a session the broker kept (cleanStart=false and a session expiry interval) gets the
 * messages that the broker queued for the session right after CONNACK, before it subscribes.
 */
class PahoMqtt5ConsumerSessionMessagesTest extends CamelTestSupport {

    private final MqttClient client = mock(MqttClient.class);
    private final AtomicReference<MqttCallback> callback = new AtomicReference<>();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        PahoMqtt5Configuration configuration = new PahoMqtt5Configuration();
        configuration.setBrokerUrl("tcp://localhost:1883");
        configuration.setCleanStart(false);
        configuration.setSessionExpiryInterval(3600);
        context.addComponent("paho-mqtt5-session", new PahoMqtt5Component(context) {
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
        });

        doAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return null;
        }).when(client).setCallback(any(MqttCallback.class));
        // the broker sends a message of the session right after CONNACK. Paho 1.2.5 (CommsCallback.messageArrived)
        // gives a message only to the callback set at that time: without one it drops the message, and for QoS 2
        // ClientState sends PUBREC and then PUBCOMP all the same, so the broker never sends it again
        doAnswer(invocation -> {
            MqttCallback current = callback.get();
            if (current != null) {
                MqttMessage message = new MqttMessage("queued while offline".getBytes(StandardCharsets.UTF_8));
                message.setQos(2);
                current.messageArrived("test", message);
            }
            return null;
        }).when(client).connect(any(MqttConnectionOptions.class));

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("paho-mqtt5-session:test").to("mock:result");
            }
        };
    }

    @Test
    void messageSentRightAfterConnackIsRouted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("queued while offline");
        mock.setResultWaitTime(5000);

        mock.assertIsSatisfied();
    }
}
