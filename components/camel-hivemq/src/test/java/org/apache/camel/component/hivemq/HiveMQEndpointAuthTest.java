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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.hivemq.client.mqtt.MqttVersion;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
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
    @DisplayName("MQTT 5: username without password builds MQTT simple auth and does not NPE")
    void mqtt5UsernameWithoutPasswordDoesNotThrow() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setMqttVersion(MqttVersion.MQTT_5_0);
        configuration.setUsername("mqtt-user");
        configuration.setPassword(null);

        Mqtt5AsyncClient client = createEndpoint(configuration).createClient()
                .getClient(Mqtt5AsyncClient.class).orElseThrow();

        assertThat(client.getConfig().getSimpleAuth()).isPresent();
        assertThat(client.getConfig().getSimpleAuth().orElseThrow().getPassword()).isEmpty();
    }

    @Test
    @DisplayName("MQTT 5: username and password are both applied to MQTT simple auth")
    void mqtt5UsernameWithPasswordSetsPassword() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setMqttVersion(MqttVersion.MQTT_5_0);
        configuration.setUsername("mqtt-user");
        configuration.setPassword("secret");

        Mqtt5AsyncClient client = createEndpoint(configuration).createClient()
                .getClient(Mqtt5AsyncClient.class).orElseThrow();

        assertThat(client.getConfig().getSimpleAuth()).isPresent();
        assertThat(client.getConfig().getSimpleAuth().orElseThrow().getPassword())
                .hasValueSatisfying(buffer -> assertThat(toBytes(buffer)).isEqualTo("secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("MQTT 3.1.1: username without password builds MQTT simple auth and does not NPE")
    void mqtt311UsernameWithoutPasswordDoesNotThrow() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setMqttVersion(MqttVersion.MQTT_3_1_1);
        configuration.setUsername("mqtt-user");
        configuration.setPassword(null);

        Mqtt3AsyncClient client = createEndpoint(configuration).createClient()
                .getClient(Mqtt3AsyncClient.class).orElseThrow();

        assertThat(client.getConfig().getSimpleAuth()).isPresent();
        assertThat(client.getConfig().getSimpleAuth().orElseThrow().getPassword()).isEmpty();
    }

    @Test
    @DisplayName("MQTT 3.1.1: username and password are both applied to MQTT simple auth")
    void mqtt311UsernameWithPasswordSetsPassword() {
        HiveMQConfiguration configuration = new HiveMQConfiguration();
        configuration.setMqttVersion(MqttVersion.MQTT_3_1_1);
        configuration.setUsername("mqtt-user");
        configuration.setPassword("secret");

        Mqtt3AsyncClient client = createEndpoint(configuration).createClient()
                .getClient(Mqtt3AsyncClient.class).orElseThrow();

        assertThat(client.getConfig().getSimpleAuth()).isPresent();
        assertThat(client.getConfig().getSimpleAuth().orElseThrow().getPassword())
                .hasValueSatisfying(buffer -> assertThat(toBytes(buffer)).isEqualTo("secret".getBytes(StandardCharsets.UTF_8)));
    }

    private HiveMQEndpoint createEndpoint(HiveMQConfiguration configuration) {
        return new HiveMQEndpoint("hivemq:test", component, configuration, "test");
    }

    private static byte[] toBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
