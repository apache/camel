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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.SendDynamicAware.DynamicAwareEntry;
import org.apache.camel.spi.annotations.SendDynamic;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HiveMQSendDynamicAwareTest {

    private DefaultCamelContext camelContext;
    private HiveMQSendDynamicAware sendDynamicAware;

    @BeforeEach
    void setUp() {
        camelContext = new DefaultCamelContext();
        sendDynamicAware = new HiveMQSendDynamicAware();
        sendDynamicAware.setCamelContext(camelContext);
        sendDynamicAware.setScheme("hivemq");
    }

    @Test
    @DisplayName("SendDynamic annotation is present for SPI discovery")
    void testSendDynamicAnnotationPresent() {
        SendDynamic annotation = HiveMQSendDynamicAware.class.getAnnotation(SendDynamic.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hivemq");
    }

    @Test
    @DisplayName("Pre-processor injects the resolved topic as OVERRIDE_TOPIC")
    void testPrepareInjectsHeader() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        DynamicAwareEntry entry = new DynamicAwareEntry(
                "hivemq:dynamic/sensors/temperature",
                "hivemq:${header.topic}",
                null,
                null);

        Processor processor = sendDynamicAware.createPreProcessor(exchange, entry);
        assertThat(processor).isNotNull();
        processor.process(exchange);

        assertThat(exchange.getMessage().getHeader(HiveMQConstants.OVERRIDE_TOPIC))
                .isEqualTo("dynamic/sensors/temperature");
    }

    @Test
    @DisplayName("Slashed URI form is parsed the same as scheme:topic")
    void testSlashedUriParsing() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        DynamicAwareEntry entry = new DynamicAwareEntry(
                "hivemq://dynamic/sensors/temperature",
                "hivemq://${header.topic}",
                null,
                null);

        Processor processor = sendDynamicAware.createPreProcessor(exchange, entry);
        processor.process(exchange);

        assertThat(exchange.getMessage().getHeader(HiveMQConstants.OVERRIDE_TOPIC))
                .isEqualTo("dynamic/sensors/temperature");
    }

    @Test
    @DisplayName("Static URI keeps the original topic path so @UriPath remains valid and reusable")
    void testResolveStaticUri() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        DynamicAwareEntry entry = new DynamicAwareEntry(
                "hivemq:devices/device-42/telemetry?host=localhost&port=1883",
                "hivemq:devices/${header.deviceId}/telemetry?host=localhost&port=1883",
                null,
                null);

        String staticUri = sendDynamicAware.resolveStaticUri(exchange, entry);

        assertThat(staticUri).isEqualTo("hivemq:devices/${header.deviceId}/telemetry?host=localhost&port=1883");
    }
}
