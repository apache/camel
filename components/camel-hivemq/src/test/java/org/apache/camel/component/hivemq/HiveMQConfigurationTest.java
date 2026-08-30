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

import com.hivemq.client.mqtt.datatypes.MqttQos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HiveMQConfigurationTest {

    @Test
    @DisplayName("Verify default configuration values")
    void testDefaults() {
        HiveMQConfiguration config = new HiveMQConfiguration();

        assertThat(config.getHost()).isEqualTo(HiveMQConstants.DEFAULT_HOST);
        assertThat(config.getPort()).isEqualTo(HiveMQConstants.DEFAULT_PORT);
        assertThat(config.getQos()).isEqualTo(MqttQos.AT_LEAST_ONCE);
        assertThat(config.isRetained()).isFalse();
        assertThat(config.isCleanStart()).isTrue();
        assertThat(config.isSsl()).isFalse();
    }

    @Test
    @DisplayName("Verify configuration copying (deep clone)")
    void testCopying() {
        HiveMQConfiguration original = new HiveMQConfiguration();
        original.setHost("broker.hivemq.com");
        original.setPort(8883);
        original.setQos(MqttQos.EXACTLY_ONCE);
        original.setRetained(true);
        original.setUsername("admin");
        original.setPassword("secret");

        HiveMQConfiguration copy = original.copy();

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.getHost()).isEqualTo("broker.hivemq.com");
        assertThat(copy.getPort()).isEqualTo(8883);
        assertThat(copy.getQos()).isEqualTo(MqttQos.EXACTLY_ONCE);
        assertThat(copy.isRetained()).isTrue();
        assertThat(copy.getUsername()).isEqualTo("admin");
        assertThat(copy.getPassword()).isEqualTo("secret");
    }
}
