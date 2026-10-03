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

import com.hivemq.client.mqtt.MqttVersion;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.hivemq.services.HiveMQService;
import org.apache.camel.test.infra.hivemq.services.HiveMQServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractHiveMQSendDynamicIT extends CamelTestSupport {

    @RegisterExtension
    public static HiveMQService HIVEMQ_SERVICE = HiveMQServiceFactory.createService();

    protected abstract MqttVersion mqttVersion();

    @Test
    @DisplayName("toD with multiple dynamic topics reuses a single hivemq endpoint")
    @SuppressWarnings("deprecation")
    public void testToDReusesSingleEndpoint() {
        String topicPrefix = getClass().getSimpleName();
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", topicPrefix + "-bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", topicPrefix + "-beer");

        long count = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith("hivemq:"))
                .count();
        assertThat(count).as("There should only be 1 hivemq endpoint").isEqualTo(1);

        String host = HIVEMQ_SERVICE.getMqttHost();
        int port = HIVEMQ_SERVICE.getMqttPort();
        String out = consumer.receiveBody(
                String.format("hivemq:%s-bar?host=%s&port=%d&mqttVersion=%s", topicPrefix, host, port, mqttVersion()),
                5000, String.class);
        assertThat(out).isEqualTo("Hello bar");
        out = consumer.receiveBody(
                String.format("hivemq:%s-beer?host=%s&port=%d&mqttVersion=%s", topicPrefix, host, port, mqttVersion()),
                5000, String.class);
        assertThat(out).isEqualTo("Hello beer");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String host = HIVEMQ_SERVICE.getMqttHost();
                int port = HIVEMQ_SERVICE.getMqttPort();

                from("direct:start")
                        .toD(String.format("hivemq:${header.where}?host=%s&port=%d&retained=true&mqttVersion=%s", host, port,
                                mqttVersion()));
            }
        };
    }
}
