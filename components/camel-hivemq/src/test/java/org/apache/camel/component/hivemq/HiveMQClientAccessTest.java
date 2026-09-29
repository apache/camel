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

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HiveMQClientAccessTest {

    private DefaultCamelContext camelContext;
    private HiveMQComponent component;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        component = new HiveMQComponent();
        component.setCamelContext(camelContext);
    }

    @AfterEach
    void tearDown() {
        camelContext.stop();
    }

    @Test
    @DisplayName("Producer exposes no client implementation before it has started")
    void producerHasNoClientBeforeStart() {
        HiveMQProducer producer = new HiveMQProducer(newEndpoint(new HiveMQConfiguration()));

        assertThat(producer.getClient(Mqtt5AsyncClient.class)).isEmpty();
    }

    @Test
    @DisplayName("Consumer exposes no client implementation before it has started")
    void consumerHasNoClientBeforeStart() {
        HiveMQConsumer consumer = new HiveMQConsumer(newEndpoint(new HiveMQConfiguration()), exchange -> {
        });

        assertThat(consumer.getClient(Mqtt5AsyncClient.class)).isEmpty();
    }

    @Test
    @DisplayName("Producer exposes the underlying Mqtt5AsyncClient once created, not the Mqtt3 type")
    void producerExposesMatchingClientTypeAfterConnectAttempt() {
        HiveMQProducer producer = new HiveMQProducer(newEndpoint(unreachableConfiguration()));

        assertThatThrownBy(producer::doStart);

        assertThat(producer.getClient(Mqtt5AsyncClient.class)).isPresent();
        assertThat(producer.getClient(Mqtt3AsyncClient.class)).isEmpty();
    }

    @Test
    @DisplayName("Consumer exposes the underlying Mqtt5AsyncClient once created, not the Mqtt3 type")
    void consumerExposesMatchingClientTypeAfterConnectAttempt() {
        HiveMQConsumer consumer = new HiveMQConsumer(newEndpoint(unreachableConfiguration()), exchange -> {
        });

        assertThatThrownBy(consumer::doStart);

        assertThat(consumer.getClient(Mqtt5AsyncClient.class)).isPresent();
        assertThat(consumer.getClient(Mqtt3AsyncClient.class)).isEmpty();
    }

    private static HiveMQConfiguration unreachableConfiguration() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setHost("127.0.0.1");
        configuration.setPort(1);
        return configuration;
    }

    private HiveMQEndpoint newEndpoint(HiveMQConfiguration configuration) {
        HiveMQEndpoint endpoint = new HiveMQEndpoint("hivemq:test", component, configuration, "test");
        endpoint.setCamelContext(camelContext);
        return endpoint;
    }
}
