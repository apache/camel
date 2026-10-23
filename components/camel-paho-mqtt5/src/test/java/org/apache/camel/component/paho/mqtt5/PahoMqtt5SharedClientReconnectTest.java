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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every consumer of a shared client subscribes again after the client reconnected.
 */
class PahoMqtt5SharedClientReconnectTest {

    @Test
    void everyConsumerOfTheClientSubscribesAgainAfterReconnect() throws Exception {
        MqttClient client = mock(MqttClient.class);
        when(client.isConnected()).thenReturn(true);

        try (CamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("sharedClient", client);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("paho-mqtt5:topicA?client=#sharedClient&qos=1").to("mock:a");
                    from("paho-mqtt5:topicB?client=#sharedClient&qos=1").to("mock:b");
                }
            });
            context.start();

            ArgumentCaptor<MqttCallback> captor = ArgumentCaptor.forClass(MqttCallback.class);
            verify(client, atLeastOnce()).setCallback(captor.capture());
            MqttCallback callback = captor.getValue();
            clearInvocations(client);

            callback.connectComplete(true, "tcp://localhost:1883");

            verify(client).subscribe("topicA", 1);
            verify(client).subscribe("topicB", 1);
        }
    }
}
