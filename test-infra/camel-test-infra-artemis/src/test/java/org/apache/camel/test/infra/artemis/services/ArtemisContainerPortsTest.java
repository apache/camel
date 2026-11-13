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
package org.apache.camel.test.infra.artemis.services;

import java.util.List;

import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil.PortConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * camel infra run artemis maps the ports of MQTT and AMQP on the host only when asked, as mosquitto and RabbitMQ use
 * the same ports (CAMEL-24823).
 */
class ArtemisContainerPortsTest {

    @Test
    void theCoreAcceptorAndTheWebConsoleByDefault() {
        assertEquals(List.of(PortConfig.primary(61616), PortConfig.secondary(8161)), ArtemisContainer.fixedPorts(null));
    }

    @Test
    void mqttAndAmqpOnRequest() {
        assertEquals(List.of(PortConfig.primary(61616), PortConfig.secondary(8161), PortConfig.secondary(1883)),
                ArtemisContainer.fixedPorts("mqtt"));
        assertEquals(List.of(PortConfig.primary(61616), PortConfig.secondary(8161), PortConfig.secondary(5672),
                PortConfig.secondary(1883)), ArtemisContainer.fixedPorts(" AMQP, mqtt,"));
        assertEquals(List.of(PortConfig.primary(61616), PortConfig.secondary(8161), PortConfig.secondary(1883),
                PortConfig.secondary(5672)), ArtemisContainer.fixedPorts("all,mqtt"));
    }

    @Test
    void anUnknownProtocolIsRefused() {
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> ArtemisContainer.fixedPorts("mqtt,stomp"));

        assertTrue(e.getMessage().contains("stomp"), e.getMessage());
        assertTrue(e.getMessage().contains("artemis.ports"), e.getMessage());
    }
}
