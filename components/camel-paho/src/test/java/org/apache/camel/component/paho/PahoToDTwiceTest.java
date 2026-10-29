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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Two toD to paho in the same exchange must publish to two topics: the topic header that the toD optimisation sets for
 * the first send must not be used by the second send.
 */
class PahoToDTwiceTest {

    @Test
    void secondToDPublishesToItsOwnTopic() throws Exception {
        MqttClient client = mock(MqttClient.class);
        try (CamelContext context = new DefaultCamelContext()) {
            PahoComponent paho = new PahoComponent();
            paho.setClient(client);
            context.addComponent("paho", paho);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .toD("paho:${header.first}")
                            .toD("paho:${header.second}");
                }
            });
            context.start();

            context.createProducerTemplate().sendBodyAndHeaders("direct:start", "Hello",
                    Map.of("first", "status/1", "second", "audit/1"));

            ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
            verify(client, times(2)).publish(topics.capture(), any(MqttMessage.class));
            assertEquals(List.of("status/1", "audit/1"), topics.getAllValues());
        }
    }

    @Test
    void loopWithToDPublishesEachIterationToItsOwnTopic() throws Exception {
        MqttClient client = mock(MqttClient.class);
        try (CamelContext context = new DefaultCamelContext()) {
            PahoComponent paho = new PahoComponent();
            paho.setClient(client);
            context.addComponent("paho", paho);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:loop")
                            .loop(3)
                            .toD("paho:devices/${exchangeProperty.CamelLoopIndex}/cmd")
                            .end();
                }
            });
            context.start();

            context.createProducerTemplate().sendBody("direct:loop", "Hello");

            ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
            verify(client, times(3)).publish(topics.capture(), any(MqttMessage.class));
            assertEquals(List.of("devices/0/cmd", "devices/1/cmd", "devices/2/cmd"), topics.getAllValues());
        }
    }
}
