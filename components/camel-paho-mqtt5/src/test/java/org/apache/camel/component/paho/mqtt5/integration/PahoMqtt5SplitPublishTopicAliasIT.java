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
package org.apache.camel.component.paho.mqtt5.integration;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Component;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A received message split into more topics than the broker's Topic Alias Maximum (mosquitto default 10) must publish
 * every part on its own topic, although all parts carry the same properties header from the received message.
 */
public class PahoMqtt5SplitPublishTopicAliasIT extends PahoMqtt5ITSupport {

    private static final String BODY = IntStream.rangeClosed(1, 12).mapToObj(String::valueOf)
            .collect(Collectors.joining(","));

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                PahoMqtt5Component paho = context.getComponent("paho-mqtt5", PahoMqtt5Component.class);
                paho.getConfiguration().setBrokerUrl("tcp://localhost:" + mqttPort);

                from("direct:in").to("paho-mqtt5:alias/in");

                from("paho-mqtt5:alias/in")
                        .convertBodyTo(String.class)
                        .split(body().tokenize(","))
                        .setHeader(PahoMqtt5Constants.CAMEL_PAHO_OVERRIDE_TOPIC, simple("alias/out/${body}"))
                        .to("paho-mqtt5:alias/out/unrouted");

                from("paho-mqtt5:alias/out/#").to("mock:out");
            }
        };
    }

    @Test
    public void everyPartArrivesOnItsOwnTopic() throws Exception {
        MockEndpoint out = getMockEndpoint("mock:out");
        out.expectedMessageCount(36);

        for (int i = 0; i < 3; i++) {
            template.sendBody("direct:in", BODY);
        }

        MockEndpoint.assertIsSatisfied(context);
        List<String> mismatches = out.getExchanges().stream()
                .map(PahoMqtt5SplitPublishTopicAliasIT::mismatch)
                .filter(m -> m != null)
                .toList();
        assertEquals(List.of(), mismatches);
    }

    private static String mismatch(Exchange exchange) {
        String body = exchange.getMessage().getBody(String.class);
        String topic = exchange.getMessage().getHeader(PahoMqtt5Constants.MQTT_TOPIC, String.class);
        return ("alias/out/" + body).equals(topic) ? null : body + " on " + topic;
    }
}
