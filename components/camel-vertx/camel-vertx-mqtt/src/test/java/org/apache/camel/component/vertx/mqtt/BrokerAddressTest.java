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
package org.apache.camel.component.vertx.mqtt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerAddressTest {

    @Test
    void plainSchemesWithTheirDefaultPort() {
        for (final String scheme : new String[] { "tcp", "mqtt", "TCP" }) {
            final BrokerAddress broker = BrokerAddress.parse(scheme + "://broker.example.com");
            assertEquals("broker.example.com", broker.host());
            assertEquals(1883, broker.port());
            assertFalse(broker.ssl());
        }
    }

    @Test
    void tlsSchemesWithTheirDefaultPort() {
        for (final String scheme : new String[] { "ssl", "mqtts" }) {
            final BrokerAddress broker = BrokerAddress.parse(scheme + "://broker.example.com/");
            assertEquals(8883, broker.port());
            assertTrue(broker.ssl());
        }
    }

    @Test
    void explicitPortAndIpv6() {
        final BrokerAddress broker = BrokerAddress.parse("tcp://[2001:db8::1]:21883");
        assertEquals("2001:db8::1", broker.host());
        assertEquals(21883, broker.port());
        assertEquals("tcp://[2001:db8::1]:21883", broker.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ws://broker.example.com", "wss://broker.example.com/mqtt" })
    void webSocketIsRefusedForNow(String url) {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> BrokerAddress.parse(url));
        assertTrue(e.getMessage().contains("WebSocket"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "broker.example.com", "http://broker.example.com", "tcp://", "tcp://broker.example.com/path",
            "tcp://broker.example.com?x=1", "tcp://broker.example.com#x" })
    void notABrokerUrl(String url) {
        assertThrows(IllegalArgumentException.class, () -> BrokerAddress.parse(url));
    }

    @Test
    void credentialsInTheUrlAreRefusedWithoutEchoingThem() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> BrokerAddress.parse("tcp://marker-user:marker-secret@broker.example.com"));
        assertFalse(e.getMessage().contains("marker-secret"), e.getMessage());
        // The URL it refused, with scheme and host, so that the user knows which one
        assertTrue(e.getMessage().contains("tcp://marker-user:"), e.getMessage());
        assertTrue(e.getMessage().contains("@broker.example.com"), e.getMessage());
    }
}
