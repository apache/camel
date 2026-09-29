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

import java.util.concurrent.TimeUnit;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class HiveMQEndpointConnectTest {

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
    @DisplayName("Unreachable broker fails connect() within a bounded time instead of hanging")
    void connectToUnreachableBrokerFailsBounded() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setHost("127.0.0.1");
        configuration.setPort(1);

        HiveMQEndpoint endpoint = new HiveMQEndpoint("hivemq:test", component, configuration, "test");
        HiveMQClientAdapter client = endpoint.createClient();

        long started = System.nanoTime();
        assertThatThrownBy(() -> endpoint.connect(client)).isInstanceOf(RuntimeCamelException.class);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(elapsedMs).isLessThan(TimeUnit.SECONDS.toMillis(HiveMQConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS));
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(client.isConnectedOrReconnecting()).isFalse());
    }

    @Test
    @DisplayName("stop() cancels automatic reconnect when the client is not CONNECTED")
    void stopClientCancelsReconnect() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setHost("127.0.0.1");
        configuration.setPort(1);

        HiveMQEndpoint endpoint = new HiveMQEndpoint("hivemq:test", component, configuration, "test");
        HiveMQClientAdapter client = endpoint.createClient();

        client.connect(true);
        client.stop();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(client.isConnectedOrReconnecting()).isFalse());
    }
}
