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

package org.apache.camel.component.hivemq;

import java.util.HashMap;
import java.util.Map;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.hivemq.services.HiveMQService;
import org.apache.camel.test.infra.hivemq.services.HiveMQServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class HiveMQQosAndRetainIT extends CamelTestSupport {

    @RegisterExtension
    public static HiveMQService HIVEMQ_SERVICE = HiveMQServiceFactory.createService();

    @EndpointInject("mock:qosResult")
    private MockEndpoint mockQosResult;

    @Produce
    private ProducerTemplate template;

    @Test
    @DisplayName("Publish with QoS 2 (EXACTLY_ONCE) and retain flag set to true")
    public void testQosAndRetainHeaders() throws Exception {
        @SuppressWarnings("deprecation")
        String host = HIVEMQ_SERVICE.getMqttHost();
        @SuppressWarnings("deprecation")
        int port = HIVEMQ_SERVICE.getMqttPort();

        mockQosResult.expectedBodiesReceived("Retained Critical Message");
        mockQosResult.expectedHeaderReceived(HiveMQConstants.MQTT_QOS, MqttQos.EXACTLY_ONCE);
        mockQosResult.expectedHeaderReceived(HiveMQConstants.MQTT_RETAINED, true);

        Map<String, Object> headers = new HashMap<>();
        headers.put(HiveMQConstants.MQTT_QOS, MqttQos.EXACTLY_ONCE);
        headers.put(HiveMQConstants.MQTT_RETAINED, true);

        // 1. Publish retained message to broker
        template.sendBodyAndHeaders("direct:startQos", "Retained Critical Message", headers);

        // 2. Start dynamic consumer route AFTER publishing so it fetches the retained message
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("hivemq:system/alerts?host=%s&port=%d&qos=EXACTLY_ONCE", host, port)
                        .to("mock:qosResult");
            }
        });

        mockQosResult.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                @SuppressWarnings("deprecation")
                String host = HIVEMQ_SERVICE.getMqttHost();
                @SuppressWarnings("deprecation")
                int port = HIVEMQ_SERVICE.getMqttPort();

                from("direct:startQos")
                        .toF("hivemq:system/alerts?host=%s&port=%d", host, port);
            }
        };
    }
}
