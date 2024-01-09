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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.paho.mqtt5.PahoMqtt5Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PahoMqtt5ToDSendDynamicIT extends PahoMqtt5ITSupport {

    @Test
    public void testToD() {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");

        // there should only be one paho endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("paho-mqtt5:")).count();
        assertEquals(1, count, "There should only be 1 paho endpoint");

        // and the messages should be in the queues
        String out = consumer.receiveBody("paho-mqtt5:bar", 2000, String.class);
        assertEquals("Hello bar", out);
        out = consumer.receiveBody("paho-mqtt5:beer", 2000, String.class);
        assertEquals("Hello beer", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                PahoMqtt5Component paho = context.getComponent("paho-mqtt5", PahoMqtt5Component.class);
                paho.getConfiguration().setBrokerUrl("tcp://localhost:" + mqttPort);

                // route message dynamic using toD
                from("direct:start").toD("paho-mqtt5:${header.where}?retained=true");
            }
        };
    }

}
