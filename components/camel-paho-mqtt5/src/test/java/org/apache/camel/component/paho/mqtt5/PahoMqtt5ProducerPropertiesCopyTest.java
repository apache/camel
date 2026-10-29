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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Every publish of a split message must hand Paho its own properties object: Paho writes the outgoing topic alias into
 * the object it is given, and the parts of a split share the properties header of the received message.
 */
class PahoMqtt5ProducerPropertiesCopyTest {

    @Test
    void eachPublishGetsItsOwnCopyOfTheProperties() throws Exception {
        // the properties of a received message, including what only the broker sends
        MqttProperties received = new MqttProperties();
        received.setPayloadFormat(true);
        received.setMessageExpiryInterval(60L);
        received.setContentType("text/plain");
        received.setResponseTopic("reply");
        received.setCorrelationData("abc".getBytes(StandardCharsets.UTF_8));
        received.setUserProperties(new ArrayList<>(List.of(new UserProperty("plant", "P1"))));
        received.setTopicAlias(7);
        received.setSubscriptionIdentifiers(new ArrayList<>(List.of(3)));

        MqttClient client = mock(MqttClient.class);
        List<MqttProperties> published = new ArrayList<>();
        List<Integer> aliasAtPublish = new ArrayList<>();
        doAnswer(invocation -> {
            // like Paho, which writes the outgoing topic alias into the properties it is given
            MqttProperties properties = invocation.getArgument(1, MqttMessage.class).getProperties();
            published.add(properties);
            aliasAtPublish.add(properties.getTopicAlias());
            properties.setTopicAlias(published.size());
            return null;
        }).when(client).publish(anyString(), any(MqttMessage.class));

        try (CamelContext context = new DefaultCamelContext()) {
            PahoMqtt5Component paho = new PahoMqtt5Component();
            paho.setClient(client);
            context.addComponent("paho-mqtt5", paho);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start")
                            .split(body().tokenize(","))
                            .setHeader(PahoMqtt5Constants.CAMEL_PAHO_OVERRIDE_TOPIC, simple("out/${body}"))
                            .to("paho-mqtt5:unrouted");
                }
            });
            context.start();

            context.createProducerTemplate().sendBodyAndHeader("direct:start", "1,2,3",
                    PahoMqtt5Constants.CAMEL_PAHO_MSG_PROPERTIES, received);

            ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
            verify(client, times(3)).publish(topics.capture(), any(MqttMessage.class));
            assertEquals(List.of("out/1", "out/2", "out/3"), topics.getAllValues());
        }

        assertEquals(3, published.size());
        aliasAtPublish.forEach(alias -> assertNull(alias, "no publish carries an alias from the header"));
        assertEquals(7, received.getTopicAlias(), "the header object is not modified by a publish");
        for (MqttProperties properties : published) {
            assertTrue(properties.getPayloadFormat());
            assertEquals(60L, properties.getMessageExpiryInterval());
            assertEquals("text/plain", properties.getContentType());
            assertEquals("reply", properties.getResponseTopic());
            assertArrayEquals(received.getCorrelationData(), properties.getCorrelationData());
            assertEquals(received.getUserProperties(), properties.getUserProperties());
            assertTrue(properties.getSubscriptionIdentifiers().isEmpty());
            assertNotSame(received, properties);
        }
        assertNotSame(published.get(0), published.get(1));
        assertNotSame(published.get(1), published.get(2));
    }
}
