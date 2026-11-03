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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * A consumer that connects to a session the broker kept (cleanSession=false) gets the messages that the broker queued
 * for the session right after CONNACK, before it subscribes.
 */
class PahoConsumerSessionMessagesTest extends CamelTestSupport {

    private final MqttClient client = mock(MqttClient.class);
    private final AtomicReference<MqttCallback> callback = new AtomicReference<>();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        PahoConfiguration configuration = new PahoConfiguration();
        configuration.setBrokerUrl("tcp://localhost:1883");
        configuration.setCleanSession(false);
        context.addComponent("paho-session", new PahoComponent(context) {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return new PahoEndpoint(uri, remaining, this, configuration.copy()) {
                    @Override
                    public Consumer createConsumer(Processor processor) throws Exception {
                        PahoConsumer consumer = new PahoConsumer(this, processor) {
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
        // gives a message only to the callback set at that time: without one it drops the message and does not
        // acknowledge it, so the broker sends it again only after the next connect
        doAnswer(invocation -> {
            MqttCallback current = callback.get();
            if (current != null) {
                MqttMessage message = new MqttMessage("queued while offline".getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                current.messageArrived("test", message);
            }
            return null;
        }).when(client).connect(any(MqttConnectOptions.class));

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("paho-session:test").to("mock:result");
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
