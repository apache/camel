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

package org.apache.camel.component.kafka.transform;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaHeaderDeserializer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KafkaHeaderDeserializerTest {

    private DefaultCamelContext camelContext;

    private final KafkaHeaderDeserializer processor = new KafkaHeaderDeserializer();

    @BeforeEach
    void setup() {
        this.camelContext = new DefaultCamelContext();
    }

    @Test
    void shouldDeserializeHeaders() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader("foo", "bar");
        exchange.getMessage().setHeader("fooBytes", "barBytes".getBytes(StandardCharsets.UTF_8));
        exchange.getMessage().setHeader("fooNull", null);
        exchange.getMessage().setHeader("number", 1L);

        processor.enabled = true;
        processor.process(exchange);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        Assertions.assertEquals("bar", exchange.getMessage().getHeader("foo"));
        Assertions.assertEquals("barBytes", exchange.getMessage().getHeader("fooBytes"));
        Assertions.assertTrue(exchange.getMessage().getHeaders().containsKey("fooNull"));
        Assertions.assertNull(exchange.getMessage().getHeader("fooNull"));
        Assertions.assertEquals("1", exchange.getMessage().getHeader("number"));
    }

    @Test
    void shouldNotDeserializeHeadersWhenDisabled() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader("foo", "bar");
        exchange.getMessage().setHeader("fooBytes", "barBytes".getBytes(StandardCharsets.UTF_8));

        processor.enabled = false;
        processor.process(exchange);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        Assertions.assertEquals("bar", exchange.getMessage().getHeader("foo"));
        Assertions.assertTrue(exchange.getMessage().getHeader("fooBytes") instanceof byte[]);
        Assertions.assertEquals(Arrays.toString("barBytes".getBytes(StandardCharsets.UTF_8)),
                Arrays.toString((byte[]) exchange.getMessage().getHeader("fooBytes")));
    }
}
