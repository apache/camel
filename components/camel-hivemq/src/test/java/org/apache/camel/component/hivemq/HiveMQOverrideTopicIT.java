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

public class HiveMQOverrideTopicIT extends CamelTestSupport {

    @RegisterExtension
    public static HiveMQService HIVEMQ_SERVICE = HiveMQServiceFactory.createService();

    @EndpointInject("mock:overrideResult")
    private MockEndpoint mockOverrideResult;

    @Produce
    private ProducerTemplate template;

    @Test
    @DisplayName("Publish to default endpoint topic but route to override topic via header")
    public void testTopicOverride() throws Exception {
        mockOverrideResult.expectedBodiesReceived("Routed to custom topic");
        mockOverrideResult.expectedHeaderReceived(HiveMQConstants.MQTT_TOPIC, "orders/processed");

        template.sendBodyAndHeader("direct:startOverride", "Routed to custom topic",
                HiveMQConstants.OVERRIDE_TOPIC, "orders/processed");

        mockOverrideResult.assertIsSatisfied();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String host = HIVEMQ_SERVICE.getMqttHost();
                int port = HIVEMQ_SERVICE.getMqttPort();

                // Endpoint points to "orders/default", but producer will override to "orders/processed"
                from("direct:startOverride")
                        .toF("hivemq:orders/default?host=%s&port=%d", host, port);

                fromF("hivemq:orders/processed?host=%s&port=%d", host, port)
                        .to("mock:overrideResult");
            }
        };
    }
}
