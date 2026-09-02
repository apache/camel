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

import java.nio.charset.StandardCharsets;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HiveMQEndpointAuthTest {

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
    @DisplayName("Username without password builds MQTT simple auth and does not NPE")
    void usernameWithoutPasswordDoesNotThrow() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setUsername("mqtt-user");
        configuration.setPassword(null);

        HiveMQEndpoint endpoint = new HiveMQEndpoint("hivemq:test", component, configuration, "test");
        Mqtt5AsyncClient client = endpoint.createClient();

        assertThat(client.getConfig().getSimpleAuth()).isPresent();
        assertThat(client.getConfig().getSimpleAuth().orElseThrow().getPassword()).isEmpty();
    }

    @Test
    @DisplayName("Username and password are both applied to MQTT simple auth")
    void usernameWithPasswordSetsPassword() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setUsername("mqtt-user");
        configuration.setPassword("secret");

        HiveMQEndpoint endpoint = new HiveMQEndpoint("hivemq:test", component, configuration, "test");
        Mqtt5AsyncClient client = endpoint.createClient();

        assertThat(client.getConfig().getSimpleAuth()).isPresent();
        assertThat(client.getConfig().getSimpleAuth().orElseThrow().getPassword())
                .hasValueSatisfying(buffer -> {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    assertThat(bytes).isEqualTo("secret".getBytes(StandardCharsets.UTF_8));
                });
    }
}
